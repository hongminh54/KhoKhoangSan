package com.hongminh54.storage.Utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.hongminh54.storage.Database.PlayerData;
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
     */
    public static void loadPlayerStats(@NotNull Player player) {
        try {
            // Lấy dữ liệu từ cơ sở dữ liệu
            PlayerData pd = MineManager.getPlayerDatabase(player);
            
            if (pd != null && pd.getStatsData() != null && !pd.getStatsData().isEmpty()) {
                String statsData = pd.getStatsData().trim();
                
                // Tạo HashMap cho người chơi
                HashMap<String, Integer> playerStats = new HashMap<>();
                
                try {
                    // Phân tích dữ liệu chuỗi
                    statsData = statsData.replace("{", "").replace("}", "").trim();
                    
                    if (!statsData.isEmpty()) {
                        String[] statPairs = statsData.split(",");
                        for (String pair : statPairs) {
                            pair = pair.trim();
                            if (!pair.isEmpty()) {
                                String[] parts = pair.split("=");
                                if (parts.length == 2) {
                                    String key = parts[0].trim();
                                    int value = Integer.parseInt(parts[1].trim());
                                    playerStats.put(key, value);
                                }
                            }
                        }
                    }
                    
                    // Đảm bảo tất cả các loại thống kê đều có giá trị
                    for (String statType : ALL_STATS) {
                        if (!playerStats.containsKey(statType)) {
                            playerStats.put(statType, 0);
                        }
                    }
                    
                    // Lưu vào cache
                    playerStatsCache.put(player.getName(), playerStats);
                    
                } catch (Exception e) {
                    Storage.getStorage().getLogger().severe("Lỗi khi phân tích dữ liệu thống kê cho " + player.getName() + ": " + e.getMessage());
                    Storage.getStorage().getLogger().severe("Dữ liệu gốc: " + statsData);
                    // Khởi tạo thống kê mặc định
                    initPlayerStats(player);
                }
            } else {
                // Nếu không có dữ liệu, khởi tạo mới
                initPlayerStats(player);
            }
        } catch (Exception e) {
            Storage.getStorage().getLogger().severe("Lỗi khi tải dữ liệu thống kê cho " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            // Khởi tạo thống kê mặc định
            initPlayerStats(player);
        }
    }
    
    /**
     * Chuyển đổi dữ liệu thống kê sang định dạng chuỗi để lưu trữ
     */
    public static String convertStatsToString(@NotNull Player player) {
        HashMap<String, Integer> statsMap = playerStatsCache.get(player.getName());
        if (statsMap == null) {
            initPlayerStats(player);
            statsMap = playerStatsCache.get(player.getName());
        }
        
        StringBuilder mapAsString = new StringBuilder("{");
        for (Map.Entry<String, Integer> entry : statsMap.entrySet()) {
            mapAsString.append(entry.getKey()).append("=").append(entry.getValue()).append(", ");
        }
        
        if (mapAsString.length() > 1) {
            mapAsString.delete(mapAsString.length() - 2, mapAsString.length());
        }
        
        mapAsString.append("}");
        String result = mapAsString.toString();
        return result;
    }
    
    /**
     * Kiểm tra và cập nhật bảng xếp hạng nếu cần
     */
    private static void checkAndUpdateLeaderboard(String statType) {
        long currentTime = System.currentTimeMillis() / 1000; // Chuyển sang giây
        Long lastUpdate = lastLeaderboardUpdate.get(statType);
        
        if (lastUpdate == null || currentTime - lastUpdate >= getLeaderboardUpdateDelay()) {
            // Cập nhật bảng xếp hạng
            LeaderboardManager.updateLeaderboard(statType);
            lastLeaderboardUpdate.put(statType, currentTime);
        }
    }
    
    /**
     * Tăng giá trị thống kê
     */
    public static void incrementStat(Player player, String statType, int amount) {
        if (!playerStatsCache.containsKey(player.getName())) {
            loadPlayerStats(player);
        }
        
        HashMap<String, Integer> statsMap = playerStatsCache.get(player.getName());
        if (statsMap.containsKey(statType)) {
            int currentValue = statsMap.get(statType);
            statsMap.put(statType, currentValue + amount);
        } else {
            statsMap.put(statType, amount);
        }

        // Lưu dữ liệu vào cơ sở dữ liệu ngay lập tức
        savePlayerStats(player);
        
        // Kiểm tra và cập nhật bảng xếp hạng nếu cần
        checkAndUpdateLeaderboard(statType);
    }
    
    /**
     * Ghi nhận hoạt động khai thác
     */
    public static void recordMining(Player player, int amount) {
        incrementStat(player, TOTAL_MINED, amount);
    }
    
    /**
     * Ghi nhận hoạt động gửi vào kho
     */
    public static void recordDeposit(Player player, int amount) {
        incrementStat(player, TOTAL_DEPOSITED, amount);
    }
    
    /**
     * Ghi nhận hoạt động rút ra khỏi kho
     */
    public static void recordWithdraw(Player player, int amount) {
        incrementStat(player, TOTAL_WITHDRAWN, amount);
    }
    
    /**
     * Ghi nhận hoạt động bán
     */
    public static void recordSell(Player player, int amount) {
        incrementStat(player, TOTAL_SOLD, amount);
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
     * Lưu dữ liệu thống kê vào cơ sở dữ liệu
     */
    public static void savePlayerStats(@NotNull Player player) {
        try {
            // Kiểm tra xem người chơi có dữ liệu trong cache không
            if (!playerStatsCache.containsKey(player.getName())) {
                Storage.getStorage().getLogger().warning("Không tìm thấy dữ liệu thống kê cho " + player.getName() + " để lưu");
                return;
            }
            
            // Lấy dữ liệu hiện tại từ cơ sở dữ liệu
            PlayerData currentData = Storage.db.getData(player.getName());
            if (currentData != null) {
                // Chuyển đổi dữ liệu thống kê thành chuỗi
                String statsDataString = convertStatsToString(player);
                
                // Tạo PlayerData mới với dữ liệu thống kê cập nhật
                PlayerData updatedData = new PlayerData(
                    player.getName(), 
                    currentData.getData(), 
                    currentData.getMax(), 
                    statsDataString
                );
                
                // Cập nhật vào cơ sở dữ liệu
                Storage.db.updateTable(updatedData);
            } else {
                // Nếu không có dữ liệu trong cơ sở dữ liệu, tạo mới
                String statsDataString = convertStatsToString(player);
                PlayerData newData = new PlayerData(
                    player.getName(),
                    "{}",
                    File.getConfig().getInt("settings.default_max_storage"),
                    statsDataString
                );
                Storage.db.createTable(newData);
            }
        } catch (Exception e) {
            Storage.getStorage().getLogger().severe("Lỗi khi lưu dữ liệu thống kê cho " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
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
            conn = Storage.db.getSQLConnection();
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
                // Nếu người chơi chưa có dữ liệu, tạo mới
                ps = conn.prepareStatement("INSERT INTO " + Storage.db.table + " (player, data, max, statsData) VALUES (?, '{}', ?, '{}')");
                ps.setString(1, playerName);
                ps.setInt(2, File.getConfig().getInt("settings.default_max_storage"));
                int affectedRows = ps.executeUpdate();
                
                if (affectedRows > 0) {
                    // Xóa khỏi cache nếu có
                    playerStatsCache.remove(playerName);
                    
                    // Cập nhật lại bảng xếp hạng
                    LeaderboardManager.updateAllLeaderboards();
                    
                    // Khôi phục trạng thái toggle
                    if (player != null) {
                        MineManager.toggle.put(player, currentToggle);
                    }
                    
                    return true;
                }
                return false;
            }
            
            // Nếu người chơi đã tồn tại, reset thống kê
            ps = conn.prepareStatement("UPDATE " + Storage.db.table + " SET statsData = '{}' WHERE player = ?");
            ps.setString(1, playerName);
            int affectedRows = ps.executeUpdate();
            
            if (affectedRows > 0) {
                // Xóa khỏi cache nếu có
                playerStatsCache.remove(playerName);
                
                // Cập nhật lại bảng xếp hạng
                LeaderboardManager.updateAllLeaderboards();
                
                // Khôi phục trạng thái toggle
                if (player != null) {
                    MineManager.toggle.put(player, currentToggle);
                }
                
                return true;
            }
            return false;
            
        } catch (SQLException e) {
            Storage.getStorage().getLogger().severe("Lỗi khi reset thống kê của người chơi: " + playerName);
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                Storage.getStorage().getLogger().severe("Không thể đóng kết nối database");
                e.printStackTrace();
            }
        }
    }
} 