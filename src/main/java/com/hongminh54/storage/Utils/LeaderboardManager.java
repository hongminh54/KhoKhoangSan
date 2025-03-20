package com.hongminh54.storage.Utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.hongminh54.storage.Storage;

/**
 * Quản lý bảng xếp hạng cho các chỉ số thống kê
 */
public class LeaderboardManager {

    // Cache cho bảng xếp hạng
    private static final Map<String, List<LeaderboardEntry>> leaderboardCache = new HashMap<>();
    
    // Cache cho dữ liệu thống kê đã phân tích
    private static final Map<String, Map<String, Integer>> parsedStatsCache = new ConcurrentHashMap<>();
    
    // Thời điểm cập nhật cache lần cuối
    private static long lastUpdateTime = 0;
    
    // Các kiểu thống kê cho bảng xếp hạng
    public static final String TYPE_MINED = "total_mined";
    public static final String TYPE_DEPOSITED = "total_deposited";
    public static final String TYPE_WITHDRAWN = "total_withdrawn";
    public static final String TYPE_SOLD = "total_sold";
    
    private static final Map<String, Long> lastLogTime = new HashMap<>();
    private static final long LOG_COOLDOWN = 60000; // 1 phút cooldown giữa các log

    private static boolean shouldLog(String type) {
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastLogTime.get(type);
        if (lastTime == null || currentTime - lastTime >= LOG_COOLDOWN) {
            lastLogTime.put(type, currentTime);
            return true;
        }
        return false;
    }
    
    /**
     * Lấy thời gian cache cho bảng xếp hạng từ cấu hình (mili giây)
     */
    private static long getCacheDuration() {
        return File.getConfig().getInt("settings.leaderboard_cache_duration", 5) * 60 * 1000;
    }
    
    /**
     * Lấy bảng xếp hạng theo loại thống kê
     * Phiên bản tối ưu: không tự động cập nhật tất cả bảng xếp hạng
     * @param type Loại thống kê
     * @param limit Giới hạn số lượng kết quả
     * @return Danh sách xếp hạng
     */
    public static List<LeaderboardEntry> getLeaderboard(String type, int limit) {
        // Kiểm tra xem cache đã hết hạn chưa
        if (isCacheExpired()) {
            // Chỉ cập nhật loại bảng xếp hạng cần thiết, không cập nhật tất cả
            updateLeaderboard(type);
            lastUpdateTime = System.currentTimeMillis();
        } else if (!leaderboardCache.containsKey(type)) {
            // Nếu không có dữ liệu trong cache, chỉ cập nhật loại cần thiết
            updateLeaderboard(type);
        }
        
        List<LeaderboardEntry> leaderboard = leaderboardCache.get(type);
        if (leaderboard == null) {
            leaderboard = new ArrayList<>();
            leaderboardCache.put(type, leaderboard);
        }
        
        // Giới hạn số lượng kết quả để tránh tạo danh sách quá lớn
        int size = Math.min(leaderboard.size(), limit);
        if (size == 0) return new ArrayList<>();
        
        // Tối ưu: sử dụng subList thay vì tạo ArrayList mới nếu có thể
        return size == leaderboard.size() ? 
               Collections.unmodifiableList(leaderboard) : 
               Collections.unmodifiableList(leaderboard.subList(0, size));
    }
    
    /**
     * Kiểm tra xem cache đã hết hạn chưa
     */
    private static boolean isCacheExpired() {
        return System.currentTimeMillis() - lastUpdateTime > getCacheDuration();
    }
    
    /**
     * Cập nhật tất cả các bảng xếp hạng
     */
    public static void updateAllLeaderboards() {
        // Xóa cache cũ
        leaderboardCache.clear();
        
        // Tối ưu: chỉ xóa các cache quá hạn, giữ lại các cache gần đây
        long currentTime = System.currentTimeMillis();
        parsedStatsCache.entrySet().removeIf(entry -> (currentTime - lastUpdateTime) > getCacheDuration() * 3);
        
        updateLeaderboard(TYPE_MINED);
        updateLeaderboard(TYPE_DEPOSITED);
        updateLeaderboard(TYPE_WITHDRAWN);
        updateLeaderboard(TYPE_SOLD);
        
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * Cập nhật tất cả các bảng xếp hạng một cách bất đồng bộ
     */
    public static void updateAllLeaderboardsAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(Storage.getStorage(), () -> {
            // Xóa cache cũ
            leaderboardCache.clear();
            
            // Tối ưu: chỉ xóa các cache quá hạn, giữ lại các cache gần đây
            long currentTime = System.currentTimeMillis();
            parsedStatsCache.entrySet().removeIf(entry -> (currentTime - lastUpdateTime) > getCacheDuration() * 3);
            
            // Cập nhật từng loại bảng xếp hạng riêng biệt
            updateLeaderboard(TYPE_MINED);
            updateLeaderboard(TYPE_DEPOSITED);
            updateLeaderboard(TYPE_WITHDRAWN);
            updateLeaderboard(TYPE_SOLD);
            
            lastUpdateTime = System.currentTimeMillis();
            
            if (Storage.getStorage().getConfig().getBoolean("settings.debug_mode", false)) {
                Storage.getStorage().getLogger().info("Đã cập nhật tất cả bảng xếp hạng (bất đồng bộ)");
            }
        });
    }
    
    /**
     * Cập nhật bảng xếp hạng theo loại thống kê - phiên bản tối ưu
     */
    public static void updateLeaderboard(String type) {
        if (!isValidStatType(type)) {
            if (shouldLog(type + "_invalid")) {
                Storage.getStorage().getLogger().warning("Loại thống kê không hợp lệ: " + type);
            }
            return;
        }

        List<LeaderboardEntry> entries = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        synchronized (leaderboardCache) {
            try {
                conn = Storage.db.getConnection();
                if (conn == null) {
                    Storage.getStorage().getLogger().severe("Không thể kết nối đến cơ sở dữ liệu khi cập nhật bảng xếp hạng");
                    return;
                }
                
                // Kiểm tra cấu trúc bảng để đảm bảo truy vấn hoạt động đúng
                boolean hasStatsDataColumn = false;
                try (ResultSet columns = conn.getMetaData().getColumns(null, null, Storage.db.table, "statsData")) {
                    hasStatsDataColumn = columns.next();
                } catch (Exception e) {
                    Storage.getStorage().getLogger().warning("Không thể kiểm tra cấu trúc bảng: " + e.getMessage());
                }
                
                if (!hasStatsDataColumn) {
                    Storage.getStorage().getLogger().severe("Cột statsData không tồn tại trong bảng " + Storage.db.table);
                    return;
                }
                
                // Sử dụng truy vấn đơn giản cho SQLite
                String sql = "SELECT player, statsData FROM " + Storage.db.table + 
                           " WHERE statsData IS NOT NULL AND statsData != '' AND statsData != '{}' " +
                           " AND statsData LIKE ? LIMIT ?";
                
                ps = conn.prepareStatement(sql);
                ps.setString(1, "%"+ type + "=%");
                ps.setInt(2, File.getConfig().getInt("settings.leaderboard_max_players", 100));
                
                rs = ps.executeQuery();
                Map<String, Integer> playerValues = new HashMap<>();
                int processedRows = 0;
                
                while (rs.next()) {
                    processedRows++;
                    String playerName = rs.getString("player");
                    if (playerName == null || playerName.trim().isEmpty()) {
                        continue;
                    }
                    
                    String statsData = rs.getString("statsData");
                    if (statsData == null || statsData.trim().isEmpty()) {
                        continue;
                    }
                    
                    try {
                        Map<String, Integer> statsMap = parseStatsData(statsData);
                        if (statsMap.containsKey(type)) {
                            int value = statsMap.get(type);
                            if (value > 0) {
                                playerValues.put(playerName, value);
                            }
                        }
                    } catch (Exception e) {
                        Storage.getStorage().getLogger().warning("Lỗi khi xử lý dữ liệu cho người chơi " + playerName + ": " + e.getMessage());
                    }
                }
                
                // Sắp xếp theo giá trị giảm dần
                playerValues.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(File.getConfig().getInt("settings.leaderboard_max_players", 100))
                    .forEach(entry -> entries.add(new LeaderboardEntry(entry.getKey(), entry.getValue())));
                
                // Cập nhật cache một cách an toàn
                leaderboardCache.put(type, entries);
                lastUpdateTime = System.currentTimeMillis();
                
                // Ghi log thành công
                if (shouldLog(type + "_update")) {
                    Storage.getStorage().getLogger().info("Đã cập nhật bảng xếp hạng " + type + 
                        " với " + entries.size() + "/" + processedRows + " người chơi");
                }
                
            } catch (SQLException e) {
                Storage.getStorage().getLogger().severe("Lỗi SQL khi cập nhật bảng xếp hạng " + type + ": " + e.getMessage());
                
                // Thử lấy dữ liệu bằng phương pháp thay thế nếu truy vấn SQL gặp lỗi
                try {
                    List<LeaderboardEntry> fallbackEntries = getFallbackLeaderboard(type);
                    if (!fallbackEntries.isEmpty()) {
                        leaderboardCache.put(type, fallbackEntries);
                        lastUpdateTime = System.currentTimeMillis();
                        Storage.getStorage().getLogger().info("Đã dùng phương pháp dự phòng để cập nhật bảng xếp hạng " + 
                            type + " với " + fallbackEntries.size() + " người chơi");
                    }
                } catch (Exception fallbackEx) {
                    Storage.getStorage().getLogger().severe("Cả phương pháp dự phòng cũng thất bại: " + fallbackEx.getMessage());
                    // Giữ nguyên cache cũ nếu còn
                    if (!leaderboardCache.containsKey(type)) {
                        leaderboardCache.put(type, new ArrayList<>());
                    }
                }
            } catch (Exception e) {
                Storage.getStorage().getLogger().severe("Lỗi không xác định khi cập nhật bảng xếp hạng " + type + ": " + e.getMessage());
                e.printStackTrace();
            } finally {
                // Đóng các kết nối
                try {
                    if (rs != null) rs.close();
                    if (ps != null) ps.close();
                    if (conn != null && Storage.db != null) {
                        Storage.db.returnConnection(conn);
                    }
                } catch (SQLException e) {
                    Storage.getStorage().getLogger().warning("Lỗi khi đóng kết nối: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Cập nhật bảng xếp hạng theo loại thống kê một cách bất đồng bộ
     */
    public static void updateLeaderboardAsync(String type) {
        if (!isValidStatType(type)) {
            if (shouldLog(type + "_invalid")) {
                Storage.getStorage().getLogger().warning("Loại thống kê không hợp lệ: " + type);
            }
            return;
        }
        
        // Chạy task cập nhật trong luồng bất đồng bộ
        Bukkit.getScheduler().runTaskAsynchronously(Storage.getStorage(), () -> {
            List<LeaderboardEntry> entries = new ArrayList<>();
            Connection conn = null;
            PreparedStatement ps = null;
            ResultSet rs = null;
            
            synchronized (leaderboardCache) {
                try {
                    conn = Storage.db.getConnection();
                    if (conn == null) {
                        Storage.getStorage().getLogger().severe("Không thể kết nối đến cơ sở dữ liệu khi cập nhật bảng xếp hạng");
                        return;
                    }
                    
                    // Kiểm tra cấu trúc bảng để đảm bảo truy vấn hoạt động đúng
                    boolean hasStatsDataColumn = false;
                    try (ResultSet columns = conn.getMetaData().getColumns(null, null, Storage.db.table, "statsData")) {
                        hasStatsDataColumn = columns.next();
                    } catch (Exception e) {
                        Storage.getStorage().getLogger().warning("Không thể kiểm tra cấu trúc bảng: " + e.getMessage());
                    }
                    
                    if (!hasStatsDataColumn) {
                        Storage.getStorage().getLogger().severe("Cột statsData không tồn tại trong bảng " + Storage.db.table);
                        return;
                    }
                    
                    // Sử dụng truy vấn đơn giản cho SQLite
                    String sql = "SELECT player, statsData FROM " + Storage.db.table + 
                               " WHERE statsData IS NOT NULL AND statsData != '' AND statsData != '{}' " +
                               " AND statsData LIKE ? LIMIT ?";
                    
                    ps = conn.prepareStatement(sql);
                    ps.setString(1, "%"+ type + "=%");
                    ps.setInt(2, File.getConfig().getInt("settings.leaderboard_max_players", 100));
                    
                    rs = ps.executeQuery();
                    Map<String, Integer> playerValues = new HashMap<>();
                    int processedRows = 0;
                    
                    while (rs.next()) {
                        processedRows++;
                        String playerName = rs.getString("player");
                        if (playerName == null || playerName.trim().isEmpty()) {
                            continue;
                        }
                        
                        String statsData = rs.getString("statsData");
                        if (statsData == null || statsData.trim().isEmpty()) {
                            continue;
                        }
                        
                        try {
                            Map<String, Integer> statsMap = parseStatsData(statsData);
                            if (statsMap.containsKey(type)) {
                                int value = statsMap.get(type);
                                if (value > 0) {
                                    playerValues.put(playerName, value);
                                }
                            }
                        } catch (Exception e) {
                            Storage.getStorage().getLogger().warning("Lỗi khi xử lý dữ liệu cho người chơi " + playerName + ": " + e.getMessage());
                        }
                    }
                    
                    // Sắp xếp theo giá trị giảm dần
                    playerValues.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(File.getConfig().getInt("settings.leaderboard_max_players", 100))
                        .forEach(entry -> entries.add(new LeaderboardEntry(entry.getKey(), entry.getValue())));
                    
                    // Cập nhật cache một cách an toàn
                    leaderboardCache.put(type, entries);
                    lastUpdateTime = System.currentTimeMillis();
                    
                    // Ghi log thành công
                    if (shouldLog(type + "_update")) {
                        Storage.getStorage().getLogger().info("Đã cập nhật bảng xếp hạng " + type + 
                            " với " + entries.size() + "/" + processedRows + " người chơi (bất đồng bộ)");
                    }
                    
                } catch (SQLException e) {
                    Storage.getStorage().getLogger().severe("Lỗi SQL khi cập nhật bảng xếp hạng " + type + ": " + e.getMessage());
                    
                    // Thử lấy dữ liệu bằng phương pháp thay thế nếu truy vấn SQL gặp lỗi
                    try {
                        List<LeaderboardEntry> fallbackEntries = getFallbackLeaderboard(type);
                        if (!fallbackEntries.isEmpty()) {
                            leaderboardCache.put(type, fallbackEntries);
                            lastUpdateTime = System.currentTimeMillis();
                            Storage.getStorage().getLogger().info("Đã dùng phương pháp dự phòng để cập nhật bảng xếp hạng " + 
                                type + " với " + fallbackEntries.size() + " người chơi (bất đồng bộ)");
                        }
                    } catch (Exception fallbackEx) {
                        Storage.getStorage().getLogger().severe("Cả phương pháp dự phòng cũng thất bại: " + fallbackEx.getMessage());
                        // Giữ nguyên cache cũ nếu còn
                        if (!leaderboardCache.containsKey(type)) {
                            leaderboardCache.put(type, new ArrayList<>());
                        }
                    }
                } catch (Exception e) {
                    Storage.getStorage().getLogger().severe("Lỗi không xác định khi cập nhật bảng xếp hạng " + type + ": " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    // Đóng các kết nối
                    try {
                        if (rs != null) rs.close();
                        if (ps != null) ps.close();
                        if (conn != null && Storage.db != null) {
                            Storage.db.returnConnection(conn);
                        }
                    } catch (SQLException e) {
                        Storage.getStorage().getLogger().warning("Lỗi khi đóng kết nối: " + e.getMessage());
                    }
                }
            }
        });
    }
    
    /**
     * Phương thức dự phòng để lấy bảng xếp hạng khi phương thức chính gặp lỗi
     */
    private static List<LeaderboardEntry> getFallbackLeaderboard(String type) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            conn = Storage.db.getConnection();
            if (conn == null) return entries;
            
            // Sử dụng truy vấn đơn giản hơn, không có điều kiện phức tạp
            String sql = "SELECT player, statsData FROM " + Storage.db.table + 
                       " WHERE statsData IS NOT NULL LIMIT ?";
            
            ps = conn.prepareStatement(sql);
            ps.setInt(1, File.getConfig().getInt("settings.leaderboard_max_players", 100) * 2); // Lấy nhiều hơn để lọc
            
            rs = ps.executeQuery();
            Map<String, Integer> playerValues = new HashMap<>();
            
            while (rs.next()) {
                String playerName = rs.getString("player");
                String statsData = rs.getString("statsData");
                
                if (playerName != null && !playerName.trim().isEmpty() && 
                    statsData != null && !statsData.trim().isEmpty()) {
                    
                    try {
                        // Phân tích dữ liệu thống kê một cách thủ công
                        int value = extractStatValueManually(statsData, type);
                        if (value > 0) {
                            playerValues.put(playerName, value);
                        }
                    } catch (Exception e) {
                        // Bỏ qua lỗi của từng người chơi
                    }
                }
            }
            
            // Sắp xếp và chuyển đổi thành danh sách LeaderboardEntry
            playerValues.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(File.getConfig().getInt("settings.leaderboard_max_players", 100))
                .forEach(entry -> entries.add(new LeaderboardEntry(entry.getKey(), entry.getValue())));
            
        } catch (Exception e) {
            Storage.getStorage().getLogger().warning("Lỗi trong phương thức dự phòng: " + e.getMessage());
        } finally {
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
                if (conn != null) Storage.db.returnConnection(conn);
            } catch (SQLException e) {
                // Bỏ qua
            }
        }
        
        return entries;
    }
    
    /**
     * Trích xuất giá trị thống kê một cách thủ công từ chuỗi JSON
     */
    private static int extractStatValueManually(String statsData, String statType) {
        try {
            // Tìm kiếm mẫu "statType=value"
            int index = statsData.indexOf(statType + "=");
            if (index != -1) {
                // Tìm giá trị sau dấu '='
                int startValueIndex = index + statType.length() + 1;
                int endValueIndex = statsData.indexOf(",", startValueIndex);
                if (endValueIndex == -1) {
                    endValueIndex = statsData.indexOf("}", startValueIndex);
                }
                if (endValueIndex == -1) {
                    endValueIndex = statsData.length();
                }
                
                if (startValueIndex < endValueIndex) {
                    String valueStr = statsData.substring(startValueIndex, endValueIndex).trim();
                    return Integer.parseInt(valueStr);
                }
            }
        } catch (Exception e) {
            // Bỏ qua lỗi
        }
        return 0;
    }
    
    /**
     * Phân tích chuỗi statsData thành Map - phiên bản tối ưu
     */
    private static Map<String, Integer> parseStatsData(String statsData) {
        Map<String, Integer> statsMap = new HashMap<>();
        
        try {
            if (statsData == null || statsData.isEmpty() || statsData.equals("{}")) {
                return statsMap;
            }
            
            // Loại bỏ khoảng trắng và ký tự đặc biệt
            statsData = statsData.trim();
            if (statsData.startsWith("{")) {
                statsData = statsData.substring(1);
            }
            if (statsData.endsWith("}")) {
                statsData = statsData.substring(0, statsData.length() - 1);
            }
            
            // Tách các cặp key-value
            String[] pairs = statsData.split(",");
            for (String pair : pairs) {
                if (pair == null || pair.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    String[] keyValue = pair.trim().split("=");
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim();
                        String valueStr = keyValue[1].trim();
                        
                        // Kiểm tra key hợp lệ
                        if (isValidStatType(key)) {
                            try {
                                int value = Integer.parseInt(valueStr);
                                if (value >= 0) { // Chỉ chấp nhận giá trị không âm
                                    statsMap.put(key, value);
                                }
                            } catch (NumberFormatException ignored) {
                                // Bỏ qua giá trị không phải số
                                Storage.getStorage().getLogger().warning("Giá trị không hợp lệ cho key " + key + ": " + valueStr);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Bỏ qua cặp key-value không hợp lệ
                    Storage.getStorage().getLogger().warning("Lỗi khi xử lý cặp key-value: " + pair);
                }
            }
        } catch (Exception e) {
            Storage.getStorage().getLogger().log(Level.WARNING, "Lỗi khi phân tích dữ liệu thống kê: " + statsData, e);
        }
        
        return statsMap;
    }
    
    /**
     * Kiểm tra xem loại thống kê có hợp lệ không
     */
    private static boolean isValidStatType(String type) {
        return TYPE_MINED.equals(type) ||
               TYPE_DEPOSITED.equals(type) ||
               TYPE_WITHDRAWN.equals(type) ||
               TYPE_SOLD.equals(type);
    }
    
    /**
     * Lấy xếp hạng của người chơi
     * 
     * @param player Người chơi
     * @param type Loại thống kê
     * @return Xếp hạng của người chơi (1-based), 0 nếu không có trong bảng xếp hạng
     */
    public static int getPlayerRank(Player player, String type) {
        // Cập nhật bảng xếp hạng nếu cần
        if (isCacheExpired() || !leaderboardCache.containsKey(type)) {
            updateLeaderboard(type);
        }
        
        List<LeaderboardEntry> leaderboard = leaderboardCache.get(type);
        if (leaderboard == null || leaderboard.isEmpty()) {
            return 0;
        }
        
        String playerName = player.getName();
        for (int i = 0; i < leaderboard.size(); i++) {
            if (leaderboard.get(i).getPlayerName().equals(playerName)) {
                return i + 1; // Xếp hạng bắt đầu từ 1
            }
        }
        
        return 0; // Không tìm thấy người chơi trong bảng xếp hạng
    }
    
    /**
     * Xóa cache bảng xếp hạng
     */
    public static void clearCache() {
        leaderboardCache.clear();
        parsedStatsCache.clear();
        lastUpdateTime = 0;
    }
    
    /**
     * Xóa cache của người chơi khi rời server
     */
    public static void removePlayerFromCache(String playerName) {
        parsedStatsCache.remove(playerName);
    }
    
    /**
     * Lấy tên hiển thị cho loại bảng xếp hạng
     * @param type Loại bảng xếp hạng
     * @return Tên hiển thị thân thiện với người dùng
     */
    public static String getTypeDisplayName(String type) {
        switch (type) {
            case TYPE_MINED:
                return "Đào Nhiều Nhất";
            case TYPE_DEPOSITED:
                return "Cất Kho Nhiều Nhất";
            case TYPE_WITHDRAWN:
                return "Rút Kho Nhiều Nhất";
            case TYPE_SOLD:
                return "Bán Nhiều Nhất";
            default:
                return "Không Xác Định";
        }
    }
    
    /**
     * Lớp đối tượng cho một mục trong bảng xếp hạng
     */
    public static class LeaderboardEntry {
        private final String playerName;
        private final int value;
        
        public LeaderboardEntry(String playerName, int value) {
            this.playerName = playerName;
            this.value = value;
        }
        
        public String getPlayerName() {
            return playerName;
        }
        
        public String getDisplayName() {
            // Cải thiện trả về tên hiển thị của người chơi
            org.bukkit.OfflinePlayer offlinePlayer = org.bukkit.Bukkit.getOfflinePlayer(playerName);
            if (offlinePlayer != null && offlinePlayer.getName() != null) {
                // Nếu người chơi trực tuyến, lấy tên hiển thị
                org.bukkit.entity.Player onlinePlayer = offlinePlayer.getPlayer();
                if (onlinePlayer != null) {
                    return onlinePlayer.getDisplayName();
                }
                // Nếu offline, trả về tên thông thường
                return offlinePlayer.getName();
            }
            return playerName; // Fallback về tên ban đầu
        }
        
        public int getValue() {
            return value;
        }
    }
    
    /**
     * Reset tất cả các thống kê về 0 và đồng bộ với cache
     * @return Số lượng người chơi đã được reset
     */
    public static int resetLeaderboard() {
        Connection conn = null;
        PreparedStatement ps = null;
        int affectedRows = 0;
        
        try {
            conn = Storage.db.getConnection();
            if (conn == null) {
                Storage.getStorage().getLogger().severe("Không thể kết nối đến database để reset bảng xếp hạng!");
                return 0;
            }
            
            // Reset tất cả các thống kê về 0
            ps = conn.prepareStatement("UPDATE " + Storage.db.table + " SET statsData = '{}'");
            affectedRows = ps.executeUpdate();
            
            // Thông báo cho người chơi online về việc reset thống kê
            for (Player player : Storage.getStorage().getServer().getOnlinePlayers()) {
                player.sendMessage(Chat.colorize("&c&l[Kho Khoáng Sản] &eThống kê của bạn đã được reset!"));
            }
            
            // Xóa cache bảng xếp hạng
            clearCache();
            
            // Cập nhật lại bảng xếp hạng với dữ liệu mới
            updateAllLeaderboards();
            
            Storage.getStorage().getLogger().info("Đã reset thống kê cho " + affectedRows + " người chơi và cập nhật bảng xếp hạng");
            
        } catch (SQLException e) {
            Storage.getStorage().getLogger().log(Level.SEVERE, "Lỗi khi reset bảng xếp hạng: " + e.getMessage(), e);
        } finally {
            try {
                if (ps != null) ps.close();
                if (conn != null) Storage.db.returnConnection(conn);
            } catch (SQLException e) {
                Storage.getStorage().getLogger().log(Level.SEVERE, "Không thể đóng kết nối: " + e.getMessage(), e);
            }
        }
        
        return affectedRows;
    }

    /**
     * Lấy tất cả người chơi từ database
     */
    private static List<LeaderboardEntry> getAllPlayers(String type) {
        List<LeaderboardEntry> result = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            conn = Storage.db.getConnection();
            ps = conn.prepareStatement("SELECT player, statsData FROM " + Storage.db.table);
            rs = ps.executeQuery();
            
            while (rs.next()) {
                String playerName = rs.getString("player");
                String statsData = rs.getString("statsData");
                
                if (statsData != null && !statsData.isEmpty()) {
                    Map<String, Integer> statsMap = parseStatsData(statsData);
                    
                    if (statsMap.containsKey(type)) {
                        result.add(new LeaderboardEntry(playerName, statsMap.get(type)));
                    }
                }
            }
        } catch (SQLException e) {
            Storage.getStorage().getLogger().log(Level.SEVERE, "Lỗi khi tải dữ liệu bảng xếp hạng", e);
        } finally {
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
                if (conn != null) Storage.db.returnConnection(conn);
            } catch (SQLException e) {
                Storage.getStorage().getLogger().log(Level.SEVERE, "Không thể đóng kết nối", e);
            }
        }
        
        return result;
    }

    /**
     * Lấy bảng xếp hạng đã cache theo loại thống kê
     * Không thực hiện cập nhật cache nếu đã hết hạn
     * @param type Loại thống kê
     * @param limit Giới hạn số lượng kết quả
     * @return Danh sách xếp hạng từ cache, hoặc danh sách trống nếu không có
     */
    public static List<LeaderboardEntry> getCachedLeaderboard(String type, int limit) {
        List<LeaderboardEntry> leaderboard = leaderboardCache.get(type);
        if (leaderboard == null) {
            return new ArrayList<>();
        }
        
        // Giới hạn số lượng kết quả
        int size = Math.min(leaderboard.size(), limit);
        if (size == 0) return new ArrayList<>();
        
        return size == leaderboard.size() ?
               Collections.unmodifiableList(leaderboard) :
               Collections.unmodifiableList(leaderboard.subList(0, size));
    }

    /**
     * Phương thức mới: Kiểm tra dữ liệu bảng xếp hạng và in thông tin gỡ lỗi
     * @param type Loại bảng xếp hạng cần kiểm tra
     * @return Số lượng người chơi trong bảng xếp hạng
     */
    public static int debugLeaderboard(String type) {
        if (!leaderboardCache.containsKey(type) || leaderboardCache.get(type).isEmpty()) {
            if (shouldLog(type + "_empty")) {
                Storage.getStorage().getLogger().info("Bảng xếp hạng " + type + " trống hoặc chưa được tạo. Đang cập nhật...");
            }
            updateLeaderboard(type);
        }
        
        List<LeaderboardEntry> entries = leaderboardCache.get(type);
        if (entries == null || entries.isEmpty()) {
            if (shouldLog(type + "_still_empty")) {
                Storage.getStorage().getLogger().warning("Bảng xếp hạng " + type + " vẫn trống sau khi cập nhật!");
            }
            return 0;
        }
        
        // Chỉ log thông tin debug khi thực sự cần thiết
        if (shouldLog(type + "_debug")) {
            Storage.getStorage().getLogger().info("=== Thông tin bảng xếp hạng " + type + " ===");
            Storage.getStorage().getLogger().info("- Số lượng người chơi: " + entries.size());
            
            // In ra 5 người đứng đầu để kiểm tra
            int count = 0;
            for (LeaderboardEntry entry : entries) {
                if (count >= 5) break;
                Storage.getStorage().getLogger().info(
                    "  #" + (count+1) + ": " + entry.getPlayerName() + " - " + 
                    Number.formatCompact(entry.getValue()) + " đơn vị"
                );
                count++;
            }
            Storage.getStorage().getLogger().info("- Thời gian cập nhật cuối: " + 
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(lastUpdateTime)));
            Storage.getStorage().getLogger().info("=====================================");
        }
        
        return entries.size();
    }

    /**
     * Debug chi tiết dữ liệu thống kê của một người chơi
     * @param playerName Tên người chơi
     */
    public static void debugPlayerStats(String playerName) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            // Lấy thông tin thống kê từ database
            conn = Storage.db.getConnection();
            ps = conn.prepareStatement(
                "SELECT statsData FROM " + Storage.db.table + " WHERE player = ?"
            );
            ps.setString(1, playerName);
            rs = ps.executeQuery();
            
            if (rs.next()) {
                String statsData = rs.getString("statsData");
                
                // Ghi log dữ liệu thô
                Storage.getStorage().getLogger().info("Dữ liệu thống kê của " + playerName + ":");
                Storage.getStorage().getLogger().info("Raw data: " + statsData);
                
                if (statsData != null && !statsData.isEmpty()) {
                    // Phân tích dữ liệu thống kê
                    Map<String, Integer> stats = parseStatsData(statsData);
                    
                    int totalMined = stats.getOrDefault(TYPE_MINED, 0);
                    int totalDeposited = stats.getOrDefault(TYPE_DEPOSITED, 0);
                    int totalWithdrawn = stats.getOrDefault(TYPE_WITHDRAWN, 0);
                    int totalSold = stats.getOrDefault(TYPE_SOLD, 0);
                    
                    // Hiển thị dữ liệu định dạng rõ ràng hơn
                    Storage.getStorage().getLogger().info("Thống kê chi tiết:");
                    Storage.getStorage().getLogger().info("- Đã khai thác: " + Number.formatCompact(totalMined) + " vật phẩm");
                    Storage.getStorage().getLogger().info("- Đã gửi vào kho: " + Number.formatCompact(totalDeposited) + " vật phẩm");
                    Storage.getStorage().getLogger().info("- Đã rút ra: " + Number.formatCompact(totalWithdrawn) + " vật phẩm");
                    Storage.getStorage().getLogger().info("- Đã bán: " + Number.formatCompact(totalSold) + " vật phẩm");
                    
                    // Kiểm tra xếp hạng
                    int rank_mined = findPlayerRankByName(playerName, TYPE_MINED);
                    int rank_deposited = findPlayerRankByName(playerName, TYPE_DEPOSITED);
                    int rank_withdrawn = findPlayerRankByName(playerName, TYPE_WITHDRAWN);
                    int rank_sold = findPlayerRankByName(playerName, TYPE_SOLD);
                    
                    Storage.getStorage().getLogger().info("Xếp hạng:");
                    Storage.getStorage().getLogger().info("- Khai thác: " + (rank_mined > 0 ? "#" + rank_mined : "Không xếp hạng") + 
                        " (" + Number.formatCompact(totalMined) + " vật phẩm)");
                    Storage.getStorage().getLogger().info("- Gửi vào kho: " + (rank_deposited > 0 ? "#" + rank_deposited : "Không xếp hạng") + 
                        " (" + Number.formatCompact(totalDeposited) + " vật phẩm)");
                    Storage.getStorage().getLogger().info("- Rút ra: " + (rank_withdrawn > 0 ? "#" + rank_withdrawn : "Không xếp hạng") + 
                        " (" + Number.formatCompact(totalWithdrawn) + " vật phẩm)");
                    Storage.getStorage().getLogger().info("- Bán: " + (rank_sold > 0 ? "#" + rank_sold : "Không xếp hạng") + 
                        " (" + Number.formatCompact(totalSold) + " vật phẩm)");
                } else {
                    Storage.getStorage().getLogger().info("Không có dữ liệu thống kê cho người chơi này");
                }
            } else {
                Storage.getStorage().getLogger().info("Không tìm thấy dữ liệu cho người chơi " + playerName);
            }
        } catch (SQLException e) {
            Storage.getStorage().getLogger().log(Level.SEVERE, "Lỗi khi debug dữ liệu thống kê: " + e.getMessage(), e);
        } finally {
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
                if (conn != null) Storage.db.returnConnection(conn);
            } catch (SQLException e) {
                Storage.getStorage().getLogger().log(Level.SEVERE, "Không thể đóng kết nối: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Tìm xếp hạng của người chơi dựa trên tên
     * @param playerName Tên người chơi
     * @param type Loại thống kê
     * @return Xếp hạng của người chơi (bắt đầu từ 1), 0 nếu không có trong bảng xếp hạng
     */
    private static int findPlayerRankByName(String playerName, String type) {
        List<LeaderboardEntry> leaderboard = leaderboardCache.get(type);
        if (leaderboard == null || leaderboard.isEmpty()) {
            return 0;
        }
        
        for (int i = 0; i < leaderboard.size(); i++) {
            if (leaderboard.get(i).getPlayerName().equals(playerName)) {
                return i + 1;
            }
        }
        
        return 0;
    }
} 