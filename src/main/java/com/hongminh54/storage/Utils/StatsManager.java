package com.hongminh54.storage.Utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.hongminh54.storage.Manager.MineManager;
import com.hongminh54.storage.Storage;

public class StatsManager {
    
    // Lưu trữ dữ liệu thống kê của người chơi trong bộ nhớ cache
    private static final HashMap<String, HashMap<String, Integer>> playerStatsCache = new HashMap<>();
    
    // Các loại thống kê
    private static final String TOTAL_MINED = "total_mined";         // Tổng số khối đã đào
    private static final String TOTAL_DEPOSITED = "total_deposited"; // Tổng số đã gửi vào kho
    private static final String TOTAL_WITHDRAWN = "total_withdrawn"; // Tổng số đã rút ra
    private static final String TOTAL_SOLD = "total_sold";           // Tổng số đã bán
    
    // Tất cả các loại thống kê hỗ trợ
    public static final String[] ALL_STATS = {TOTAL_MINED, TOTAL_DEPOSITED, TOTAL_WITHDRAWN, TOTAL_SOLD};
    
    // Map lưu thời điểm cập nhật bảng xếp hạng lần cuối cho mỗi loại thống kê
    private static final Map<String, Long> lastLeaderboardUpdate = new ConcurrentHashMap<>();
    
    // Map lưu thời điểm thông báo đầy kho lần cuối cho mỗi người chơi
    private static final Map<String, Long> lastStorageFullNotification = new ConcurrentHashMap<>();
    
    /**
     * Thời gian trễ giữa các lần thông báo đầy kho (giây)
     */
    private static final int STORAGE_FULL_NOTIFICATION_DELAY = 30;
    
    private static final Map<String, Long> lastLogTime = new HashMap<>();
    private static final long LOG_COOLDOWN = 60000; // 1 phút cooldown
    
    // Batch processing cho stats để tránh deadlock
    private static final Queue<StatsUpdate> statsPendingUpdates = new ConcurrentLinkedQueue<>();
    private static int STATS_BATCH_SIZE = 50; // Số lượng update tối đa trong một batch
    private static long STATS_BATCH_TIMEOUT = 15000; // 15 giây timeout cho batch
    private static int STATS_PROCESS_INTERVAL = 45; // Số tick giữa các lần xử lý
    private static boolean STATS_BATCH_ENABLED = true; // Bật/tắt stats batch processing
    private static boolean LOG_STATS_BATCH = true; // Bật/tắt log xử lý batch
    private static boolean isProcessingStats = false; // Tránh nhiều thread xử lý cùng lúc
    private static long lastStatsProcessTime = System.currentTimeMillis();
    
    /**
     * Tải cấu hình stats batch processing từ config.yml
     */
    public static void loadStatsConfig() {
        try {
            FileConfiguration config = File.getConfig();
            if (config.contains("stats_batch_processing")) {
                STATS_BATCH_ENABLED = config.getBoolean("stats_batch_processing.enabled", true);
                
                // Chỉ cập nhật nếu batch processing được bật
                if (STATS_BATCH_ENABLED) {
                    STATS_BATCH_SIZE = config.getInt("stats_batch_processing.batch_size", 50);
                    STATS_BATCH_TIMEOUT = config.getLong("stats_batch_processing.batch_timeout", 15000);
                    STATS_PROCESS_INTERVAL = config.getInt("stats_batch_processing.process_interval", 45);
                    LOG_STATS_BATCH = config.getBoolean("stats_batch_processing.log_batch_processing", true);
                    
                    Storage.getStorage().getLogger().info("Đã tải cấu hình stats batch processing: " +
                            "batch_size=" + STATS_BATCH_SIZE + ", " +
                            "batch_timeout=" + STATS_BATCH_TIMEOUT + "ms, " +
                            "process_interval=" + STATS_PROCESS_INTERVAL + " ticks");
                } else {
                    Storage.getStorage().getLogger().info("Stats batch processing đã bị tắt trong cấu hình");
                }
            }
        } catch (Exception e) {
            Storage.getStorage().getLogger().warning("Lỗi khi tải cấu hình stats batch processing: " + e.getMessage());
            // Giữ nguyên các giá trị mặc định nếu có lỗi
        }
    }
    
    // Stats update class để lưu trữ thông tin cập nhật
    private static class StatsUpdate {
        private final String playerName;
        private final String statsData;
        
        public StatsUpdate(String playerName, String statsData) {
            this.playerName = playerName;
            this.statsData = statsData;
        }
        
        public String getPlayerName() {
            return playerName;
        }
        
        public String getStatsData() {
            return statsData;
        }
    }
    
    /**
     * Thêm cập nhật stats vào hàng đợi xử lý
     */
    private static void addStatsToQueue(Player player) {
        // Chỉ thêm vào hàng đợi nếu có dữ liệu
        if (player != null && playerStatsCache.containsKey(player.getName())) {
            String statsData = convertStatsToString(player);
            statsPendingUpdates.add(new StatsUpdate(player.getName(), statsData));
            
            // Kiểm tra xem có cần xử lý batch không
            long now = System.currentTimeMillis();
            if (statsPendingUpdates.size() >= STATS_BATCH_SIZE || 
                    (now - lastStatsProcessTime >= STATS_BATCH_TIMEOUT && !statsPendingUpdates.isEmpty())) {
                processStatsBatch();
            }
        }
    }
    
    /**
     * Xử lý batch cập nhật thống kê
     */
    public static void processStatsBatch() {
        // Nếu đang xử lý hoặc batch processing bị tắt, bỏ qua
        if (isProcessingStats || !STATS_BATCH_ENABLED) {
            return;
        }
        
        // Kiểm tra xem có cập nhật nào cần xử lý không
        if (statsPendingUpdates.isEmpty()) {
            return;
        }
        
        // Kiểm tra xem đã đến thời gian xử lý tiếp theo chưa
        long now = System.currentTimeMillis();
        long elapsed = now - lastStatsProcessTime;
        if (elapsed < STATS_BATCH_TIMEOUT && statsPendingUpdates.size() < STATS_BATCH_SIZE) {
            return;
        }
        
        isProcessingStats = true;
        
        // Khởi tạo danh sách batch
        final List<StatsUpdate> batchUpdates = new ArrayList<>();
        
        try {
            // Lấy các cập nhật từ hàng đợi
            int count = 0;
            while (!statsPendingUpdates.isEmpty() && count < STATS_BATCH_SIZE) {
                StatsUpdate update = statsPendingUpdates.poll();
                if (update != null) {
                    batchUpdates.add(update);
                    count++;
                }
            }
            
            if (batchUpdates.isEmpty()) {
                isProcessingStats = false;
                return;
            }
            
            // Xử lý batch trong một thread riêng biệt
            Bukkit.getScheduler().runTaskAsynchronously(Storage.getStorage(), () -> {
                Connection conn = null;
                PreparedStatement ps = null;
                
                try {
                    // Thực hiện cập nhật theo batch
                    conn = Storage.db.getConnection();
                    if (conn == null) {
                        // Thêm lại vào hàng đợi nếu không thể kết nối
                        for (StatsUpdate update : batchUpdates) {
                            statsPendingUpdates.add(update);
                        }
                        return;
                    }
                    
                    // Xử lý đồng bộ hóa - sử dụng khóa đối tượng Database để đảm bảo chỉ một luồng thực hiện transaction
                    synchronized (Storage.db) {
                        // Lưu trạng thái auto-commit hiện tại
                        boolean wasAutoCommit = true;
                        try {
                            wasAutoCommit = conn.getAutoCommit();
                        } catch (SQLException ex) {
                            Storage.getStorage().getLogger().warning("Không thể lấy trạng thái auto-commit: " + ex.getMessage());
                        }
                        
                        try {
                            // Tắt autocommit để sử dụng transaction
                            conn.setAutoCommit(false);
                            
                            // Tăng timeout cho SQLite để tránh SQLITE_BUSY
                            try {
                                conn.createStatement().execute("PRAGMA busy_timeout = 30000");
                            } catch (Exception e) {
                                // Bỏ qua nếu không hỗ trợ
                            }
                            
                            // Chuẩn bị statement
                            ps = conn.prepareStatement("UPDATE " + Storage.db.table + " SET statsData = ? WHERE player = ?");
                            
                            // Nhóm các cập nhật theo người chơi để tối ưu hóa lưu trữ
                            Map<String, String> consolidatedUpdates = new HashMap<>();
                            
                            // Tổng hợp các cập nhật cho cùng một người chơi
                            for (StatsUpdate update : batchUpdates) {
                                consolidatedUpdates.put(update.getPlayerName(), update.getStatsData());
                            }
                            
                            // Thêm các cập nhật vào batch
                            for (Map.Entry<String, String> entry : consolidatedUpdates.entrySet()) {
                                String playerName = entry.getKey();
                                String statsData = entry.getValue();
                                
                                // Nén dữ liệu trước khi lưu
                                statsData = CompressUtils.compressString(statsData);
                                
                                ps.setString(1, statsData);
                                ps.setString(2, playerName);
                                ps.addBatch();
                            }
                            
                            // Thực hiện batch
                            ps.executeBatch();
                            
                            // Commit transaction
                            conn.commit();
                            
                            // Ghi log với thông tin số lượng cập nhật
                            if (LOG_STATS_BATCH && shouldLog("stats_batch")) {
                                Storage.getStorage().getLogger().info("Đã cập nhật thống kê cho " + consolidatedUpdates.size() + " người chơi (từ " + batchUpdates.size() + " cập nhật)");
                            }
                            
                            // Cập nhật thời gian cuối cùng xử lý
                            lastStatsProcessTime = System.currentTimeMillis();
                            
                        } catch (SQLException ex) {
                            // Kiểm tra xem có phải lỗi do database bị khóa không
                            boolean isSQLiteBusy = ex.getMessage() != null &&
                                (ex.getMessage().contains("SQLITE_BUSY") ||
                                 ex.getMessage().contains("database is locked") ||
                                 ex.getMessage().contains("database table is locked"));
                            
                            if (isSQLiteBusy) {
                                Storage.getStorage().getLogger().warning("Database bị khóa khi cập nhật batch stats, thêm lại vào hàng đợi để thử lại sau");
                                
                                // Thêm lại vào hàng đợi nếu lỗi SQLITE_BUSY
                                for (StatsUpdate update : batchUpdates) {
                                    statsPendingUpdates.add(update);
                                }
                            } else {
                                Storage.getStorage().getLogger().log(Level.SEVERE, "Lỗi SQL khi cập nhật batch stats: " + ex.getMessage(), ex);
                            }
                            
                            // Rollback transaction nếu có lỗi
                            try {
                                if (conn != null && !conn.getAutoCommit()) {
                                    conn.rollback();
                                }
                            } catch (SQLException e) {
                                Storage.getStorage().getLogger().log(Level.SEVERE, "Lỗi khi rollback transaction: " + e.getMessage(), e);
                            }
                        } finally {
                            // Khôi phục trạng thái auto-commit ban đầu
                            try {
                                if (conn != null && !conn.isClosed() && conn.getAutoCommit() != wasAutoCommit) {
                                    conn.setAutoCommit(wasAutoCommit);
                                }
                            } catch (SQLException ex) {
                                Storage.getStorage().getLogger().warning("Không thể khôi phục trạng thái auto-commit: " + ex.getMessage());
                            }
                            
                            // Đóng statement
                            if (ps != null) {
                                try {
                                    ps.close();
                                } catch (SQLException e) {
                                    Storage.getStorage().getLogger().warning("Không thể đóng statement: " + e.getMessage());
                                }
                            }
                        }
                    } // End synchronized block
                    
                } catch (Exception e) {
                    Storage.getStorage().getLogger().log(Level.SEVERE, "Lỗi không xác định khi xử lý batch stats: " + e.getMessage(), e);
                    
                    // Thêm lại vào hàng đợi nếu có lỗi
                    for (StatsUpdate update : batchUpdates) {
                        statsPendingUpdates.add(update);
                    }
                } finally {
                    // Trả kết nối về pool
                    if (conn != null) {
                        Storage.db.returnConnection(conn);
                    }
                    
                    // Đánh dấu là đã xử lý xong để có thể tiếp tục trong lần tiếp theo
                    isProcessingStats = false;
                }
            });
        } catch (Exception e) {
            Storage.getStorage().getLogger().log(Level.SEVERE, "Lỗi khi chuẩn bị batch stats: " + e.getMessage(), e);
            isProcessingStats = false;
        }
    }
    
    /**
     * Lưu tất cả các stats đang chờ xử lý (gọi khi tắt server)
     */
    public static void saveAllPendingStats() {
        // Kiểm tra nếu không có gì để lưu
        if (statsPendingUpdates.isEmpty()) {
            return;
        }
        
        Storage.getStorage().getLogger().info("Đang lưu " + statsPendingUpdates.size() + " cập nhật thống kê còn lại...");
        
        // Số lần retry tối đa và thời gian chờ
        final int MAX_RETRY = 5;
        final int RETRY_DELAY = 200;
        
        // Tạo bản sao của hàng đợi để xử lý
        List<StatsUpdate> allUpdates = new ArrayList<>();
        while (!statsPendingUpdates.isEmpty()) {
            StatsUpdate update = statsPendingUpdates.poll();
            if (update != null) {
                allUpdates.add(update);
            }
        }
        
        if (allUpdates.isEmpty()) return;
        
        boolean success = false;
        
        // Thử lưu với retry
        for (int attempt = 0; attempt < MAX_RETRY && !success; attempt++) {
            // Xử lý đồng bộ khi tắt server
            Connection conn = null;
            PreparedStatement ps = null;
            
            try {
                conn = Storage.db.getConnection();
                if (conn == null) {
                    Storage.getStorage().getLogger().severe("Không thể kết nối đến database để lưu thống kê (lần thử " + (attempt + 1) + ")");
                    
                    if (attempt < MAX_RETRY - 1) {
                        try {
                            Thread.sleep(RETRY_DELAY * (attempt + 1));
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                        continue;
                    } else {
                        // Nếu hết số lần thử, thêm lại vào hàng đợi
                        for (StatsUpdate update : allUpdates) {
                            statsPendingUpdates.add(update);
                        }
                        return;
                    }
                }
                
                // Lưu trạng thái auto-commit hiện tại
                boolean wasAutoCommit = conn.getAutoCommit();
                
                try {
                    // Tăng timeout để giảm khả năng SQLITE_BUSY
                    try (Statement timeoutStmt = conn.createStatement()) {
                        timeoutStmt.execute("PRAGMA busy_timeout = 30000");
                    }
                    
                    // Tắt autocommit để sử dụng transaction
                    conn.setAutoCommit(false);
                    
                    // Chuẩn bị statement
                    ps = conn.prepareStatement("UPDATE " + Storage.db.table + " SET statsData = ? WHERE player = ?");
                    ps.setQueryTimeout(30); // 30 giây timeout
                    
                    int processedCount = 0;
                    
                    // Xử lý tất cả các update
                    for (StatsUpdate update : allUpdates) {
                        ps.setString(1, update.getStatsData());
                        ps.setString(2, update.getPlayerName());
                        ps.addBatch();
                        processedCount++;
                        
                        // Thực hiện batch mỗi 50 cập nhật
                        if (processedCount % 50 == 0) {
                            ps.executeBatch();
                            ps.clearBatch();
                        }
                    }
                    
                    // Thực hiện batch cuối cùng nếu còn
                    if (processedCount % 50 != 0) {
                        ps.executeBatch();
                    }
                    
                    // Kiểm tra trạng thái auto-commit trước khi commit
                    if (!conn.getAutoCommit()) {
                        // Commit transaction
                        conn.commit();
                    }
                    
                    Storage.getStorage().getLogger().info("Đã lưu thành công " + processedCount + " cập nhật thống kê");
                    success = true;
                } finally {
                    // Khôi phục trạng thái auto-commit ban đầu
                    try {
                        if (conn != null && !conn.isClosed() && conn.getAutoCommit() != wasAutoCommit) {
                            conn.setAutoCommit(wasAutoCommit);
                        }
                    } catch (SQLException ex) {
                        Storage.getStorage().getLogger().warning("Không thể khôi phục trạng thái auto-commit: " + ex.getMessage());
                    }
                }
                
            } catch (SQLException e) {
                // Kiểm tra nếu lỗi là SQLITE_BUSY
                boolean isSQLiteBusy = e.getMessage() != null && 
                    (e.getMessage().contains("SQLITE_BUSY") || 
                     e.getMessage().contains("database is locked") ||
                     e.getMessage().contains("database table is locked") ||
                     e.getMessage().contains("cannot start a transaction"));
                
                if (isSQLiteBusy && attempt < MAX_RETRY - 1) {
                    Storage.getStorage().getLogger().warning("Database bị khóa khi lưu thống kê, thử lại lần " 
                        + (attempt + 2) + "/" + MAX_RETRY);
                    
                    // Rollback transaction nếu có lỗi và không ở chế độ auto-commit
                    if (conn != null) {
                        try {
                            if (!conn.getAutoCommit()) {
                                conn.rollback();
                            }
                        } catch (SQLException ex) {
                            Storage.getStorage().getLogger().log(Level.SEVERE, "Lỗi khi rollback transaction: " + ex.getMessage(), ex);
                        }
                    }
                    
                    try {
                        Thread.sleep(RETRY_DELAY * (attempt + 1));
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    Storage.getStorage().getLogger().log(Level.SEVERE, "Lỗi khi lưu thống kê (lần thử " + (attempt + 1) + "): " + e.getMessage(), e);
                    
                    // Rollback transaction nếu có lỗi và không ở chế độ auto-commit
                    if (conn != null) {
                        try {
                            if (!conn.getAutoCommit()) {
                                conn.rollback();
                            }
                        } catch (SQLException ex) {
                            Storage.getStorage().getLogger().log(Level.SEVERE, "Lỗi khi rollback transaction: " + ex.getMessage(), ex);
                        }
                    }
                }
            } finally {
                // Đóng kết nối và statement
                try {
                    if (ps != null) ps.close();
                    if (conn != null) {
                        // Đảm bảo auto-commit được bật trước khi trả về pool
                        try {
                            if (!conn.getAutoCommit()) {
                                conn.setAutoCommit(true);
                            }
                        } catch (SQLException ex) {
                            Storage.getStorage().getLogger().warning("Không thể bật auto-commit: " + ex.getMessage());
                        }
                        // Đóng kết nối
                        Storage.db.returnConnection(conn);
                    }
                } catch (SQLException e) {
                    Storage.getStorage().getLogger().log(Level.SEVERE, "Lỗi khi đóng kết nối database: " + e.getMessage(), e);
                }
            }
        }
        
        // Nếu thất bại sau tất cả các lần thử
        if (!success) {
            Storage.getStorage().getLogger().warning("Không thể lưu thống kê sau " + MAX_RETRY + " lần thử");
            
            // Thêm lại vào hàng đợi để lưu sau
            for (StatsUpdate update : allUpdates) {
                statsPendingUpdates.add(update);
            }
        }
    }
    
    /**
     * Gửi thông báo đầy kho cho người chơi
     */
    public static void sendStorageFullNotification(Player player, String material, int currentAmount, int maxAmount) {
        long currentTime = System.currentTimeMillis() / 1000;
        Long lastNotification = lastStorageFullNotification.get(player.getName());
        
        // Chỉ gửi thông báo nếu đã đủ thời gian trễ
        if (lastNotification == null || currentTime - lastNotification >= STORAGE_FULL_NOTIFICATION_DELAY) {
            String materialName = File.getConfig().getString("items." + material);
            if (materialName == null) {
                materialName = material.replace("_", " ");
            }
            
            // Tạo thông báo chi tiết
            String message = Chat.colorize("&c&l[Kho Khoáng Sản] &eKho của bạn đã đầy!");
            String details = Chat.colorize("&7Vật phẩm: &f" + materialName);
            String amount = Chat.colorize("&7Số lượng: &f" + currentAmount + "&7/&f" + maxAmount);
            String suggestion = Chat.colorize("&7Vui lòng rút bớt vật phẩm để có thể khai thác tiếp!");
            
            // Gửi thông báo
            player.sendMessage("");
            player.sendMessage(message);
            player.sendMessage(details);
            player.sendMessage(amount);
            player.sendMessage(suggestion);
            player.sendMessage("");
            
            // Cập nhật thời gian thông báo cuối cùng
            lastStorageFullNotification.put(player.getName(), currentTime);
        }
    }
    
    /**
     * Xóa thông báo đầy kho khỏi cache khi người chơi đăng xuất
     */
    public static void removeStorageFullNotification(String playerName) {
        lastStorageFullNotification.remove(playerName);
    }
    
    /**
     * Lấy thời gian trễ cập nhật bảng xếp hạng từ config
     */
    private static int getLeaderboardUpdateDelay() {
        return File.getConfig().getInt("settings.leaderboard_update_delay", 30);
    }
    
    /**
     * Khởi tạo dữ liệu thống kê mới cho người chơi
     */
    public static void initPlayerStats(Player player) {
        HashMap<String, Integer> statsMap = new HashMap<>();
        statsMap.put(TOTAL_MINED, 0);
        statsMap.put(TOTAL_DEPOSITED, 0);
        statsMap.put(TOTAL_WITHDRAWN, 0);
        statsMap.put(TOTAL_SOLD, 0);
        
        playerStatsCache.put(player.getName(), statsMap);
    }
    
    /**
     * Tải dữ liệu thống kê của người chơi từ cơ sở dữ liệu
     * @param player Người chơi cần tải dữ liệu
     * @param forceRefresh true nếu muốn tải lại từ database bất kể cache
     */
    public static void loadPlayerStats(@NotNull Player player, boolean forceRefresh) {
        if (player == null) return;
        
        // Nếu không yêu cầu tải lại và đã có dữ liệu trong cache thì không làm gì
        if (!forceRefresh && playerStatsCache.containsKey(player.getName())) {
            return;
        }
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            conn = Storage.db.getConnection();
            if (conn == null) {
                Storage.getStorage().getLogger().severe("Không thể kết nối đến database để tải thống kê");
                return;
            }
            
            ps = conn.prepareStatement("SELECT statsData FROM " + Storage.db.table + " WHERE player = ?");
            ps.setString(1, player.getName());
            rs = ps.executeQuery();
            
            HashMap<String, Integer> statsMap = new HashMap<>();
            
            if (rs.next()) {
                String statsData = rs.getString("statsData");
                if (statsData != null && !statsData.isEmpty() && !statsData.equals("{}")) {
                    // Phân tích dữ liệu thống kê
                    statsData = statsData.substring(1, statsData.length() - 1); // Bỏ {}
                    String[] pairs = statsData.split(",");
                    
                    for (String pair : pairs) {
                        try {
                            String[] keyValue = pair.split("=");
                            if (keyValue.length == 2) {
                                String key = keyValue[0].trim();
                                int value = Integer.parseInt(keyValue[1].trim());
                                if (value >= 0) { // Chỉ chấp nhận giá trị không âm
                                    statsMap.put(key, value);
                                }
                            }
                        } catch (Exception e) {
                            Storage.getStorage().getLogger().warning("Lỗi khi phân tích dữ liệu thống kê cho " + player.getName() + ": " + pair);
                        }
                    }
                }
            }
            
            // Đảm bảo có tất cả các loại thống kê
            statsMap.putIfAbsent(TOTAL_MINED, 0);
            statsMap.putIfAbsent(TOTAL_DEPOSITED, 0);
            statsMap.putIfAbsent(TOTAL_WITHDRAWN, 0);
            statsMap.putIfAbsent(TOTAL_SOLD, 0);
            
            // Lưu vào cache
            playerStatsCache.put(player.getName(), statsMap);
            
            if (forceRefresh) {
                Storage.getStorage().getLogger().info("Đã tải lại dữ liệu thống kê của " + player.getName() + " từ database");
            }
            
        } catch (SQLException e) {
            Storage.getStorage().getLogger().log(Level.SEVERE, "Lỗi khi tải thống kê cho " + player.getName() + ": " + e.getMessage(), e);
            // Khởi tạo dữ liệu mới nếu có lỗi
            initPlayerStats(player);
        } finally {
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
                if (conn != null) Storage.db.returnConnection(conn);
            } catch (SQLException e) {
                Storage.getStorage().getLogger().log(Level.SEVERE, "Không thể đóng kết nối database", e);
            }
        }
    }
    
    /**
     * Tải dữ liệu thống kê của người chơi (overload cho tương thích ngược)
     * @param player Người chơi cần tải dữ liệu
     */
    public static void loadPlayerStats(@NotNull Player player) {
        loadPlayerStats(player, false);
    }
    
    /**
     * Chuyển đổi dữ liệu thống kê của người chơi sang dạng chuỗi để lưu vào cơ sở dữ liệu
     * @param player Người chơi
     * @return Chuỗi dữ liệu thống kê đã được định dạng
     */
    public static String convertStatsToString(@NotNull Player player) {
        if (!playerStatsCache.containsKey(player.getName())) {
            return "{}";
        }
        
        HashMap<String, Integer> stats = playerStatsCache.get(player.getName());
        if (stats.isEmpty()) {
            return "{}";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        
        boolean first = true;
        for (Map.Entry<String, Integer> entry : stats.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        
        sb.append("}");
        
        // Nén dữ liệu thống kê nếu đủ lớn
        return CompressUtils.compressString(sb.toString());
    }
    
    private static boolean shouldLog(String key) {
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastLogTime.get(key);
        if (lastTime == null || currentTime - lastTime >= LOG_COOLDOWN) {
            lastLogTime.put(key, currentTime);
            return true;
        }
        return false;
    }
    
    /**
     * Kiểm tra và cập nhật bảng xếp hạng nếu cần
     */
    private static void checkAndUpdateLeaderboard(String statType) {
        long currentTime = System.currentTimeMillis() / 1000;
        Long lastUpdate = lastLeaderboardUpdate.get(statType);
        
        if (lastUpdate == null || currentTime - lastUpdate >= getLeaderboardUpdateDelay()) {
            try {
                // Chỉ log khi cần thiết
                if (shouldLog("leaderboard_update_" + statType)) {
                    Storage.getStorage().getLogger().info("Đang cập nhật bảng xếp hạng cho loại: " + statType);
                }
                
                LeaderboardManager.updateLeaderboard(statType);
                lastLeaderboardUpdate.put(statType, currentTime);
            } catch (Exception e) {
                if (shouldLog("leaderboard_error_" + statType)) {
                    Storage.getStorage().getLogger().log(Level.WARNING, 
                        "Lỗi khi cập nhật bảng xếp hạng " + statType + ": " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Tăng giá trị thống kê
     */
    public static void incrementStat(Player player, String statType, int amount) {
        if (amount < 0) {
            Storage.getStorage().getLogger().warning("Không thể tăng số liệu âm cho " + player.getName() + ": " + amount);
            return;
        }

        if (!playerStatsCache.containsKey(player.getName())) {
            loadPlayerStats(player);
        }
        
        HashMap<String, Integer> statsMap = playerStatsCache.get(player.getName());
        if (statsMap == null) {
            initPlayerStats(player);
            statsMap = playerStatsCache.get(player.getName());
        }

        synchronized (statsMap) {
            int currentValue = statsMap.getOrDefault(statType, 0);
            // Kiểm tra tràn số
            if (currentValue + amount < 0) {
                Storage.getStorage().getLogger().warning("Phát hiện tràn số cho " + player.getName() + " ở " + statType);
                statsMap.put(statType, Integer.MAX_VALUE);
            } else {
                statsMap.put(statType, currentValue + amount);
            }
            
            // Lưu vào bộ nhớ cache
            playerStatsCache.put(player.getName(), statsMap);
        }

        // Lưu dữ liệu vào cơ sở dữ liệu ngay lập tức
        savePlayerStats(player);
    }
    
    /**
     * Ghi nhận hoạt động khai thác
     * @param player Người chơi
     * @param amount Số lượng block (luôn là 1 vì chỉ tính số block đã đào)
     */
    public static void recordMining(Player player, int amount) {
        // Đảm bảo chỉ tính 1 block mỗi lần gọi
        if (amount != 1) {
            Storage.getStorage().getLogger().warning("Phát hiện ghi nhận số block không hợp lệ: " + amount + " cho " + player.getName());
            amount = 1;
        }
        
        incrementStat(player, TOTAL_MINED, amount);
        
        // Đảm bảo lưu vào database ngay lập tức
        savePlayerStats(player);
        
        // Kiểm tra và cập nhật bảng xếp hạng nếu cần
        checkAndUpdateLeaderboard(TOTAL_MINED);
    }
    
    /**
     * Ghi nhận hoạt động gửi vào kho
     */
    public static void recordDeposit(Player player, int amount) {
        incrementStat(player, TOTAL_DEPOSITED, amount);
        
        // Đảm bảo lưu vào database ngay lập tức
        savePlayerStats(player);
        
        // Cập nhật bảng xếp hạng
        checkAndUpdateLeaderboard(TOTAL_DEPOSITED);
    }
    
    /**
     * Ghi nhận hoạt động rút ra khỏi kho
     */
    public static void recordWithdraw(Player player, int amount) {
        incrementStat(player, TOTAL_WITHDRAWN, amount);
        
        // Đảm bảo lưu vào database ngay lập tức
        savePlayerStats(player);
        
        // Cập nhật bảng xếp hạng
        checkAndUpdateLeaderboard(TOTAL_WITHDRAWN);
    }
    
    /**
     * Ghi nhận hoạt động bán
     */
    public static void recordSell(Player player, int amount) {
        incrementStat(player, TOTAL_SOLD, amount);
        
        // Đảm bảo lưu vào database ngay lập tức
        savePlayerStats(player);
        
        // Cập nhật bảng xếp hạng
        checkAndUpdateLeaderboard(TOTAL_SOLD);
    }
    
    /**
     * Lấy tổng số đã khai thác
     */
    public static int getTotalMined(Player player) {
        if (!playerStatsCache.containsKey(player.getName())) {
            loadPlayerStats(player);
        }
        return playerStatsCache.get(player.getName()).getOrDefault(TOTAL_MINED, 0);
    }
    
    /**
     * Lấy tổng số đã gửi vào kho
     */
    public static int getTotalDeposited(Player player) {
        if (!playerStatsCache.containsKey(player.getName())) {
            loadPlayerStats(player);
        }
        return playerStatsCache.get(player.getName()).getOrDefault(TOTAL_DEPOSITED, 0);
    }
    
    /**
     * Lấy tổng số đã rút
     */
    public static int getTotalWithdrawn(Player player) {
        if (!playerStatsCache.containsKey(player.getName())) {
            loadPlayerStats(player);
        }
        return playerStatsCache.get(player.getName()).getOrDefault(TOTAL_WITHDRAWN, 0);
    }
    
    /**
     * Lấy tổng số đã bán
     */
    public static int getTotalSold(Player player) {
        if (!playerStatsCache.containsKey(player.getName())) {
            loadPlayerStats(player);
        }
        return playerStatsCache.get(player.getName()).getOrDefault(TOTAL_SOLD, 0);
    }
    
    /**
     * Lấy danh sách các thông tin thống kê
     */
    public static List<String> getStatsInfo(Player player) {
        if (!playerStatsCache.containsKey(player.getName())) {
            loadPlayerStats(player);
        }
        
        List<String> info = new ArrayList<>();
        HashMap<String, Integer> statsMap = playerStatsCache.get(player.getName());
        
        info.add("&eTổng đã khai thác: &f" + statsMap.getOrDefault(TOTAL_MINED, 0) + " vật phẩm");
        info.add("&eTổng đã gửi vào kho: &f" + statsMap.getOrDefault(TOTAL_DEPOSITED, 0) + " vật phẩm");
        info.add("&eTổng đã rút ra: &f" + statsMap.getOrDefault(TOTAL_WITHDRAWN, 0) + " vật phẩm");
        info.add("&eTổng đã bán: &f" + statsMap.getOrDefault(TOTAL_SOLD, 0) + " vật phẩm");
        
        return info;
    }
    
    /**
     * Xóa dữ liệu thống kê của người chơi khỏi bộ nhớ cache khi họ đăng xuất
     * @param playerName Tên của người chơi
     */
    public static void removeFromCache(String playerName) {
        if (playerStatsCache.containsKey(playerName)) {
            playerStatsCache.remove(playerName);
            Storage.getStorage().getLogger().info("Đã xóa dữ liệu thống kê khỏi cache cho người chơi " + playerName);
        }
    }
    
    /**
     * Lưu dữ liệu thống kê của người chơi
     * @param player Người chơi cần lưu dữ liệu
     */
    public static void savePlayerStats(Player player) {
        if (player == null) return;
        
        try {
            // Thay vì lưu trực tiếp, chỉ thêm vào hàng đợi xử lý
            addStatsToQueue(player);
        } catch (Exception e) {
            Storage.getStorage().getLogger().log(Level.SEVERE, "Lỗi khi thêm stats vào hàng đợi: " + e.getMessage(), e);
        }
    }
    
    /**
     * Lưu dữ liệu thống kê của người chơi bất đồng bộ
     * Phương thức này sử dụng batch processing để tránh deadlock
     */
    public static void savePlayerStatsAsync(Player player) {
        if (player == null) return;
        
        // Thêm vào hàng đợi thay vì lưu riêng lẻ
        addStatsToQueue(player);
    }
    
    /**
     * Reset thống kê của một người chơi cụ thể
     * 
     * @param playerName Tên người chơi
     * @return true nếu reset thành công, false nếu thất bại
     */
    public static boolean resetPlayerStats(String playerName) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            conn = Storage.db.getConnection();
            if (conn == null) {
                Storage.getStorage().getLogger().severe("Không thể kết nối đến database!");
                return false;
            }
            
            // Lưu trạng thái toggle hiện tại
            boolean currentToggle = false;
            Player player = Bukkit.getPlayer(playerName);
            if (player != null) {
                currentToggle = MineManager.toggle.getOrDefault(player, false);
            }
            
            // Kiểm tra xem người chơi có tồn tại trong database không
            ps = conn.prepareStatement("SELECT COUNT(*) FROM " + Storage.db.table + " WHERE player = ?");
            ps.setString(1, playerName);
            rs = ps.executeQuery();
            rs.next();
            boolean playerExists = rs.getInt(1) > 0;
            
            if (!playerExists) {
                return false;
            }
            
            // Reset dữ liệu thống kê
            ps.close();
            ps = conn.prepareStatement("UPDATE " + Storage.db.table + " SET statsData = '{}' WHERE player = ?");
            ps.setString(1, playerName);
            int rowsAffected = ps.executeUpdate();
            
            // Xóa khỏi cache
            if (playerStatsCache.containsKey(playerName)) {
                playerStatsCache.remove(playerName);
            }
            
            if (player != null) {
                // Tạo mới dữ liệu thống kê trong cache
                HashMap<String, Integer> stats = new HashMap<>();
                for (String stat : ALL_STATS) {
                    stats.put(stat, 0);
                }
                playerStatsCache.put(playerName, stats);
                
                // Khôi phục trạng thái toggle
                MineManager.toggle.put(player, currentToggle);
            }
            
            return rowsAffected > 0;
        } catch (SQLException e) {
            Storage.getStorage().getLogger().log(Level.SEVERE, "Lỗi khi reset thống kê người chơi: " + e.getMessage(), e);
            return false;
        } finally {
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
                if (conn != null) Storage.db.returnConnection(conn);
            } catch (SQLException e) {
                Storage.getStorage().getLogger().log(Level.SEVERE, "Lỗi khi đóng kết nối: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Phương thức debug để kiểm tra dữ liệu thống kê của người chơi
     */
    public static void debugPlayerStats(Player player) {
        if (!playerStatsCache.containsKey(player.getName())) {
            loadPlayerStats(player);
        }
        
        HashMap<String, Integer> statsMap = playerStatsCache.get(player.getName());
        if (statsMap == null) {
            Storage.getStorage().getLogger().warning("Không tìm thấy dữ liệu thống kê cho " + player.getName());
            return;
        }
        
        Storage.getStorage().getLogger().info("=== Debug Stats cho " + player.getName() + " ===");
        Storage.getStorage().getLogger().info("Tổng đã khai thác: " + statsMap.getOrDefault(TOTAL_MINED, 0));
        Storage.getStorage().getLogger().info("Tổng đã gửi vào kho: " + statsMap.getOrDefault(TOTAL_DEPOSITED, 0));
        Storage.getStorage().getLogger().info("Tổng đã rút ra: " + statsMap.getOrDefault(TOTAL_WITHDRAWN, 0));
        Storage.getStorage().getLogger().info("Tổng đã bán: " + statsMap.getOrDefault(TOTAL_SOLD, 0));
    }
} 