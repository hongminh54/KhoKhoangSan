package com.hongminh54.storage.Utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import com.hongminh54.storage.Database.PlayerData;
import com.hongminh54.storage.Storage;

/**
 * Tiện ích tối ưu hóa cơ sở dữ liệu SQLite
 */
public class DatabaseOptimizer {
    
    // Thời gian giữa mỗi lần tối ưu hóa (ticks)
    private static final long OPTIMIZATION_INTERVAL = 72000L; // 1 giờ
    
    // Biến theo dõi nhiệm vụ tối ưu hóa định kỳ
    private static BukkitTask optimizationTask = null;
    
    // Cờ đánh dấu đang thực hiện tối ưu hóa
    private static boolean isOptimizing = false;
    
    // Thời điểm tối ưu hóa lần cuối
    private static long lastOptimizationTime = 0;
    
    // Cờ đánh dấu tối ưu dữ liệu người chơi
    private static final boolean OPTIMIZE_PLAYER_DATA = true;
    
    /**
     * Khởi tạo nhiệm vụ tối ưu hóa cơ sở dữ liệu định kỳ
     */
    public static void initialize() {
        long interval = File.getConfig().getLong("database.optimization_interval", OPTIMIZATION_INTERVAL);
        
        // Hủy nhiệm vụ cũ nếu có
        if (optimizationTask != null) {
            optimizationTask.cancel();
        }
        
        // Tạo nhiệm vụ mới
        optimizationTask = Bukkit.getScheduler().runTaskTimerAsynchronously(Storage.getStorage(), () -> {
            // Chỉ thực hiện khi server không nhiều người chơi (dưới 5 người)
            if (Bukkit.getOnlinePlayers().size() < 5) {
                optimizeDatabase();
            }
        }, interval, interval);
        
        Storage.getStorage().getLogger().info("Đã khởi tạo nhiệm vụ tối ưu hóa cơ sở dữ liệu định kỳ");
    }
    
    /**
     * Tối ưu hóa cơ sở dữ liệu
     */
    public static void optimizeDatabase() {
        if (isOptimizing) {
            return;
        }
        
        try {
            isOptimizing = true;
            
            Storage.getStorage().getLogger().info("Bắt đầu tối ưu hóa cơ sở dữ liệu...");
            
            // Tối ưu dữ liệu của người chơi nếu bật
            if (OPTIMIZE_PLAYER_DATA) {
                optimizePlayerData();
            }
            
            // Thực hiện các PRAGMA cho SQLite
            runOptimizationCommands();
            
            // Cập nhật thời gian tối ưu hóa lần cuối
            lastOptimizationTime = System.currentTimeMillis();
            
            Storage.getStorage().getLogger().info("Hoàn thành tối ưu hóa cơ sở dữ liệu");
        } finally {
            isOptimizing = false;
        }
    }
    
    /**
     * Thực hiện các lệnh PRAGMA để tối ưu SQLite
     */
    private static void runOptimizationCommands() {
        Connection conn = null;
        Statement stmt = null;
        
        try {
            conn = Storage.db.getConnection();
            if (conn == null) {
                Storage.getStorage().getLogger().warning("Không thể kết nối đến cơ sở dữ liệu để tối ưu hóa");
                return;
            }
            
            // Lưu trạng thái auto-commit
            boolean wasAutoCommit = conn.getAutoCommit();
            
            try {
                // Tắt auto-commit
                conn.setAutoCommit(false);
                
                stmt = conn.createStatement();
                
                // Phân tích lại cơ sở dữ liệu để tối ưu hóa
                Storage.getStorage().getLogger().info("Phân tích và tối ưu hóa SQLite...");
                
                // Kiểm tra tính toàn vẹn
                stmt.execute("PRAGMA integrity_check");
                
                // Tối ưu hóa cơ sở dữ liệu
                stmt.execute("PRAGMA optimize");
                
                // Phân tích lại indexes
                stmt.execute("ANALYZE");
                
                // Xóa không gian không sử dụng
                stmt.execute("VACUUM");
                
                // Nén cơ sở dữ liệu
                stmt.execute("PRAGMA auto_vacuum = INCREMENTAL");
                stmt.execute("PRAGMA incremental_vacuum");
                
                conn.commit();
                
                Storage.getStorage().getLogger().info("Đã tối ưu hóa cơ sở dữ liệu SQLite thành công");
            } finally {
                // Khôi phục trạng thái auto-commit
                if (conn != null && !conn.isClosed()) {
                    conn.setAutoCommit(wasAutoCommit);
                }
            }
        } catch (SQLException e) {
            Storage.getStorage().getLogger().log(Level.WARNING, "Lỗi khi tối ưu hóa cơ sở dữ liệu: " + e.getMessage(), e);
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) Storage.db.returnConnection(conn);
            } catch (SQLException e) {
                Storage.getStorage().getLogger().log(Level.WARNING, "Lỗi khi đóng kết nối: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Tối ưu hóa dữ liệu người chơi: nén dữ liệu, xóa người chơi không hoạt động lâu
     */
    private static void optimizePlayerData() {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            conn = Storage.db.getConnection();
            if (conn == null) {
                Storage.getStorage().getLogger().warning("Không thể kết nối đến cơ sở dữ liệu để tối ưu hóa dữ liệu người chơi");
                return;
            }
            
            // Lưu trạng thái auto-commit
            boolean wasAutoCommit = conn.getAutoCommit();
            
            try {
                // Tắt auto-commit để sử dụng transaction
                conn.setAutoCommit(false);
                
                // Tăng timeout cho SQLite để tránh SQLITE_BUSY
                try {
                    conn.createStatement().execute("PRAGMA busy_timeout = 30000");
                } catch (Exception e) {
                    // Bỏ qua nếu không hỗ trợ
                }
                
                Storage.getStorage().getLogger().info("Nén dữ liệu người chơi...");
                
                // Lấy dữ liệu của tất cả người chơi để nén
                ps = conn.prepareStatement("SELECT player, data, max, statsData FROM " + Storage.db.table);
                rs = ps.executeQuery();
                
                Map<String, PlayerData> updatedData = new HashMap<>();
                int compressedCount = 0;
                
                while (rs.next()) {
                    String playerName = rs.getString("player");
                    String data = rs.getString("data");
                    int max = rs.getInt("max");
                    String statsData = rs.getString("statsData");
                    
                    boolean needsUpdate = false;
                    
                    // Kiểm tra xem data có thể nén không
                    if (data != null && !data.isEmpty() && !CompressUtils.isCompressed(data)) {
                        String compressedData = CompressUtils.compressString(data);
                        if (!compressedData.equals(data)) {
                            data = compressedData;
                            needsUpdate = true;
                        }
                    }
                    
                    // Kiểm tra xem statsData có thể nén không
                    if (statsData != null && !statsData.isEmpty() && !CompressUtils.isCompressed(statsData)) {
                        String compressedStatsData = CompressUtils.compressString(statsData);
                        if (!compressedStatsData.equals(statsData)) {
                            statsData = compressedStatsData;
                            needsUpdate = true;
                        }
                    }
                    
                    // Nếu có thay đổi, thêm vào danh sách cập nhật
                    if (needsUpdate) {
                        updatedData.put(playerName, new PlayerData(playerName, data, max, statsData));
                        compressedCount++;
                    }
                }
                
                // Đóng ResultSet và PreparedStatement
                rs.close();
                ps.close();
                
                // Cập nhật dữ liệu đã nén
                if (!updatedData.isEmpty()) {
                    ps = conn.prepareStatement("UPDATE " + Storage.db.table + " SET data = ?, statsData = ? WHERE player = ?");
                    
                    for (PlayerData playerData : updatedData.values()) {
                        ps.setString(1, playerData.getData());
                        ps.setString(2, playerData.getStatsData());
                        ps.setString(3, playerData.getPlayer());
                        ps.addBatch();
                    }
                    
                    ps.executeBatch();
                    conn.commit();
                    
                    Storage.getStorage().getLogger().info("Đã nén dữ liệu của " + compressedCount + " người chơi");
                }
                
            } finally {
                // Khôi phục trạng thái auto-commit
                if (conn != null && !conn.isClosed()) {
                    conn.setAutoCommit(wasAutoCommit);
                }
            }
        } catch (SQLException e) {
            Storage.getStorage().getLogger().log(Level.WARNING, "Lỗi khi tối ưu hóa dữ liệu người chơi: " + e.getMessage(), e);
        } finally {
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
                if (conn != null) Storage.db.returnConnection(conn);
            } catch (SQLException e) {
                Storage.getStorage().getLogger().log(Level.WARNING, "Lỗi khi đóng kết nối: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Hủy nhiệm vụ tối ưu hóa
     */
    public static void shutdown() {
        if (optimizationTask != null) {
            optimizationTask.cancel();
            optimizationTask = null;
        }
    }
} 