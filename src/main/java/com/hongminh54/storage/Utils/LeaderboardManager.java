package com.hongminh54.storage.Utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    
    // Thời điểm cập nhật cache lần cuối
    private static long lastUpdateTime = 0;
    
    // Các kiểu thống kê cho bảng xếp hạng
    public static final String TYPE_MINED = "total_mined";
    public static final String TYPE_DEPOSITED = "total_deposited";
    public static final String TYPE_WITHDRAWN = "total_withdrawn";
    public static final String TYPE_SOLD = "total_sold";
    
    /**
     * Lấy thời gian cache bảng xếp hạng từ config (tính bằng mili giây)
     */
    private static long getCacheDuration() {
        return File.getConfig().getInt("settings.leaderboard_cache_duration", 5) * 60 * 1000;
    }
    
    /**
     * Lấy bảng xếp hạng theo loại thống kê
     * 
     * @param type Loại thống kê (mined, deposited, withdrawn, sold)
     * @param limit Giới hạn số lượng kết quả
     * @return Danh sách xếp hạng
     */
    public static List<LeaderboardEntry> getLeaderboard(String type, int limit) {
        // Kiểm tra xem cache đã hết hạn chưa
        if (isCacheExpired()) {
            updateAllLeaderboards();
        }
        
        // Nếu không có dữ liệu trong cache, cập nhật bảng xếp hạng
        if (!leaderboardCache.containsKey(type)) {
            updateLeaderboard(type);
        }
        
        List<LeaderboardEntry> leaderboard = leaderboardCache.get(type);
        if (leaderboard == null) {
            leaderboard = new ArrayList<>();
            leaderboardCache.put(type, leaderboard);
        }
        
        // Trả về danh sách giới hạn theo số lượng yêu cầu
        return leaderboard.size() <= limit ? leaderboard : leaderboard.subList(0, limit);
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
        
        updateLeaderboard(TYPE_MINED);
        updateLeaderboard(TYPE_DEPOSITED);
        updateLeaderboard(TYPE_WITHDRAWN);
        updateLeaderboard(TYPE_SOLD);
        
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * Cập nhật bảng xếp hạng theo loại thống kê
     */
    public static void updateLeaderboard(String type) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            conn = Storage.db.getSQLConnection();
            ps = conn.prepareStatement("SELECT player, statsData FROM " + Storage.db.table);
            rs = ps.executeQuery();
            
            while (rs.next()) {
                String playerName = rs.getString("player");
                String statsData = rs.getString("statsData");
                
                if (statsData != null && !statsData.isEmpty() && !statsData.equals("{}")) {
                    Map<String, Integer> statsMap = parseStatsData(statsData);
                    
                    if (statsMap.containsKey(type)) {
                        int value = statsMap.get(type);
                        if (value > 0) {
                            entries.add(new LeaderboardEntry(playerName, value));
                        }
                    }
                }
            }
            
            // Sắp xếp theo thứ tự giảm dần dựa trên giá trị
            entries.sort(Comparator.comparingInt(LeaderboardEntry::getValue).reversed());
            
            // Cập nhật cache
            leaderboardCache.put(type, entries);
            
        } catch (SQLException e) {
            Storage.getStorage().getLogger().log(Level.SEVERE, "Không thể tải dữ liệu bảng xếp hạng", e);
        } finally {
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                Storage.getStorage().getLogger().log(Level.SEVERE, "Không thể đóng kết nối", e);
            }
        }
    }
    
    /**
     * Phân tích chuỗi statsData thành Map
     */
    private static Map<String, Integer> parseStatsData(String statsData) {
        Map<String, Integer> statsMap = new HashMap<>();
        
        try {
            statsData = statsData.replace("{", "").replace("}", "").trim();
            if (!statsData.isEmpty()) {
                String[] statItems = statsData.split(",");
                for (String statItem : statItems) {
                    statItem = statItem.trim();
                    if (!statItem.isEmpty()) {
                        String[] stat = statItem.split("=");
                        if (stat.length == 2) {
                            String key = stat[0].trim();
                            int value = Number.getInteger(stat[1].trim());
                            statsMap.put(key, value);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Storage.getStorage().getLogger().log(Level.WARNING, "Lỗi khi phân tích dữ liệu thống kê: " + statsData, e);
        }
        
        return statsMap;
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
        lastUpdateTime = 0;
    }
    
    /**
     * Lấy tên hiển thị của loại thống kê
     * 
     * @param type Loại thống kê
     * @return Tên hiển thị
     */
    public static String getTypeDisplayName(String type) {
        if (TYPE_MINED.equals(type)) {
            return Chat.colorizewp("&bĐã khai thác");
        } else if (TYPE_DEPOSITED.equals(type)) {
            return Chat.colorizewp("&6Đã gửi vào kho");
        } else if (TYPE_WITHDRAWN.equals(type)) {
            return Chat.colorizewp("&aĐã rút ra");
        } else if (TYPE_SOLD.equals(type)) {
            return Chat.colorizewp("&eĐã bán");
        } else {
            return Chat.colorizewp("&7Không xác định");
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
        
        public int getValue() {
            return value;
        }
        
        /**
         * Lấy tên hiển thị của người chơi (nếu online) hoặc tên thường
         */
        public String getDisplayName() {
            Player player = Bukkit.getPlayer(playerName);
            return player != null ? player.getDisplayName() : playerName;
        }
    }
    
    /**
     * Reset tất cả các thống kê về 0
     * 
     * @return Số lượng người chơi đã được reset
     */
    public static int resetLeaderboard() {
        Connection conn = null;
        PreparedStatement ps = null;
        int affectedRows = 0;
        
        try {
            conn = Storage.db.getSQLConnection();
            // Reset tất cả các thống kê về 0
            ps = conn.prepareStatement("UPDATE " + Storage.db.table + " SET statsData = '{}'");
            affectedRows = ps.executeUpdate();
            
            // Xóa cache
            clearCache();
            
            // Cập nhật lại bảng xếp hạng
            updateAllLeaderboards();
            
        } catch (SQLException e) {
            Storage.getStorage().getLogger().log(Level.SEVERE, "Không thể reset bảng xếp hạng", e);
        } finally {
            try {
                if (ps != null) ps.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                Storage.getStorage().getLogger().log(Level.SEVERE, "Không thể đóng kết nối", e);
            }
        }
        
        return affectedRows;
    }
} 