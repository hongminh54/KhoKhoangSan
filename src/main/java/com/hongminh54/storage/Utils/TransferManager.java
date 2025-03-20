package com.hongminh54.storage.Utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import com.hongminh54.storage.Database.TransferHistory;
import com.hongminh54.storage.Manager.MineManager;
import com.hongminh54.storage.Storage;

/**
 * Quản lý các giao dịch chuyển tài nguyên
 */
public class TransferManager {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private static final SimpleDateFormat DB_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final String TRANSFER_HISTORY_TABLE = "transfer_history";
    // Cache để kiểm tra thời gian chờ
    private static final ConcurrentHashMap<UUID, Long> lastTransferTime = new ConcurrentHashMap<>();
    // Thời gian chờ mặc định giữa các lần chuyển (giây)
    private static final int DEFAULT_COOLDOWN = 5;

    /**
     * Chuyển đổi chuỗi thời gian sang dạng long
     * @param timestamp Chuỗi thời gian theo định dạng "yyyy-MM-dd HH:mm:ss"
     * @return Thời gian dạng long (milli giây từ epoch)
     */
    public static long parseTimestamp(String timestamp) {
        try {
            if (timestamp == null || timestamp.isEmpty()) {
                return System.currentTimeMillis();
            }
            
            synchronized (DB_DATE_FORMAT) {
                Date date = DB_DATE_FORMAT.parse(timestamp);
                return date.getTime();
            }
        } catch (Exception e) {
            Storage.getStorage().getLogger().warning("Lỗi khi phân tích chuỗi thời gian: " + e.getMessage());
            return System.currentTimeMillis();
        }
    }
    
    /**
     * Chuyển đổi thời gian dạng long sang chuỗi theo định dạng cơ sở dữ liệu
     * @param timestamp Thời gian dạng long (milli giây từ epoch)
     * @return Chuỗi thời gian theo định dạng "yyyy-MM-dd HH:mm:ss"
     */
    public static String formatTimestamp(long timestamp) {
        synchronized (DB_DATE_FORMAT) {
            return DB_DATE_FORMAT.format(new Date(timestamp));
        }
    }

    /**
     * Lưu lịch sử giao dịch vào cơ sở dữ liệu
     * @param sender Người gửi
     * @param receiver Người nhận
     * @param material Loại tài nguyên
     * @param amount Số lượng
     * @return true nếu lưu thành công, false nếu thất bại
     */
    public static boolean recordTransfer(Player sender, Player receiver, String material, int amount) {
        // Kiểm tra xem có bật ghi lịch sử không
        if (!File.getConfig().getBoolean("transfer.log_history", true)) {
            return true;
        }
        
        // Cập nhật thời gian chuyển giao gần nhất
        lastTransferTime.put(sender.getUniqueId(), System.currentTimeMillis());
        
        Connection conn = null;
        PreparedStatement ps = null;
        boolean success = false;
        
        try {
            conn = Storage.db.getConnection();
            if (conn == null) {
                Storage.getStorage().getLogger().severe("Không thể kết nối đến cơ sở dữ liệu để ghi lịch sử chuyển giao");
                return false;
            }
            
            // Lưu trạng thái auto-commit hiện tại
            boolean wasAutoCommit = false;
            try {
                wasAutoCommit = conn.getAutoCommit();
            } catch (SQLException ex) {
                Storage.getStorage().getLogger().warning("Không thể lấy trạng thái auto-commit: " + ex.getMessage());
            }
            
            try {
                // Tắt auto-commit để sử dụng transaction
                conn.setAutoCommit(false);
                
                // Tăng timeout cho SQLite để tránh SQLITE_BUSY
                try {
                    conn.createStatement().execute("PRAGMA busy_timeout = 30000");
                } catch (Exception e) {
                    // Bỏ qua nếu không hỗ trợ
                }
                
                String insertSQL = 
                    "INSERT INTO " + TRANSFER_HISTORY_TABLE + 
                    " (sender, receiver, material, amount, timestamp) VALUES (?, ?, ?, ?, ?)";
                
                ps = conn.prepareStatement(insertSQL);
                ps.setString(1, sender.getName());
                ps.setString(2, receiver.getName());
                ps.setString(3, material);
                ps.setInt(4, amount);
                ps.setString(5, formatTimestamp(System.currentTimeMillis()));
                
                int result = ps.executeUpdate();
                
                // Commit transaction
                conn.commit();
                
                success = result > 0;
                
            } catch (SQLException ex) {
                // Kiểm tra xem có phải lỗi do database bị khóa không
                boolean isSQLiteBusy = ex.getMessage() != null &&
                    (ex.getMessage().contains("SQLITE_BUSY") ||
                     ex.getMessage().contains("database is locked") ||
                     ex.getMessage().contains("database table is locked"));
                
                if (isSQLiteBusy) {
                    Storage.getStorage().getLogger().warning("Database bị khóa khi ghi lịch sử chuyển giao, sẽ thử lại sau");
                } else {
                    Storage.getStorage().getLogger().severe("Lỗi khi ghi lịch sử chuyển giao: " + ex.getMessage());
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
            }
            
        } finally {
            // Đóng tất cả tài nguyên
            try {
                if (ps != null) ps.close();
                if (conn != null) {
                    // Đảm bảo auto-commit được bật trước khi trả kết nối về pool
                    try {
                        if (!conn.getAutoCommit()) {
                            conn.setAutoCommit(true);
                        }
                    } catch (SQLException e) {
                        Storage.getStorage().getLogger().warning("Không thể bật auto-commit: " + e.getMessage());
                    }
                    Storage.db.returnConnection(conn);
                }
            } catch (SQLException e) {
                Storage.getStorage().getLogger().log(Level.SEVERE, "Lỗi khi đóng kết nối: " + e.getMessage(), e);
            }
        }
        
        return success;
    }
    
    /**
     * Lấy lịch sử giao dịch của người chơi (cả gửi và nhận)
     * @param playerName Tên người chơi
     * @param limit Số lượng tối đa lịch sử trả về
     * @return Danh sách lịch sử giao dịch
     */
    public static List<TransferHistory> getPlayerTransferHistory(String playerName, int limit) {
        List<TransferHistory> history = new ArrayList<>();
        
        // Kiểm tra bảng tồn tại
        if (!isTableExists()) {
            // Tạo bảng nếu chưa tồn tại
            if (!createTransferHistoryTable()) {
                Storage.getStorage().getLogger().warning("Không thể tạo bảng lịch sử giao dịch. Trả về danh sách trống.");
                return history;
            }
        }
        
        // Truy vấn SQL để lấy lịch sử giao dịch
        String sql = "SELECT * FROM " + TRANSFER_HISTORY_TABLE + " WHERE sender = ? OR receiver = ? ORDER BY timestamp DESC LIMIT ?";
        
        try (Connection conn = Storage.db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, playerName);
            ps.setString(2, playerName);
            ps.setInt(3, limit);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TransferHistory transfer = new TransferHistory(
                        rs.getInt("id"),
                        rs.getString("sender"),
                        rs.getString("receiver"),
                        rs.getString("material"),
                        rs.getInt("amount"),
                        parseTimestamp(rs.getString("timestamp"))
                    );
                    
                    history.add(transfer);
                }
            }
        } catch (SQLException e) {
            Storage.getStorage().getLogger().severe("Lỗi khi lấy lịch sử giao dịch: " + e.getMessage());
            e.printStackTrace();
        }
        
        return history;
    }
    
    /**
     * Kiểm tra bảng storage_transfers đã tồn tại chưa
     * @return true nếu bảng đã tồn tại, false nếu chưa
     */
    public static boolean isTableExists() {
        String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
        
        try (Connection conn = Storage.db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, TRANSFER_HISTORY_TABLE);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return true; // Bảng mới tồn tại
                }
            }
            
            // Kiểm tra bảng cũ
            PreparedStatement psOld = conn.prepareStatement("SELECT name FROM sqlite_master WHERE type='table' AND name='storage_transfers'");
            try (ResultSet rs = psOld.executeQuery()) {
                if (rs.next()) {
                    // Bảng cũ tồn tại, tiến hành di chuyển dữ liệu
                    migrateTransferHistory(conn);
                    return true;
                }
            }
            
            return false;
        } catch (SQLException e) {
            Storage.getStorage().getLogger().severe("Lỗi khi kiểm tra bảng lịch sử giao dịch: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Di chuyển dữ liệu từ bảng cũ sang bảng mới
     * @param conn Kết nối cơ sở dữ liệu
     */
    private static void migrateTransferHistory(Connection conn) {
        Storage.getStorage().getLogger().info("Bắt đầu di chuyển dữ liệu từ bảng cũ sang bảng mới...");
        
        try {
            // Tạo bảng mới nếu chưa tồn tại
            String createTableSQL = 
                "CREATE TABLE IF NOT EXISTS " + TRANSFER_HISTORY_TABLE + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sender TEXT NOT NULL, " +
                "receiver TEXT NOT NULL, " +
                "material TEXT NOT NULL, " +
                "amount INTEGER NOT NULL, " +
                "timestamp TEXT NOT NULL)";
            
            try (PreparedStatement ps = conn.prepareStatement(createTableSQL)) {
                ps.executeUpdate();
            }
            
            // Lấy dữ liệu từ bảng cũ
            String selectSQL = "SELECT * FROM storage_transfers";
            try (PreparedStatement ps = conn.prepareStatement(selectSQL);
                 ResultSet rs = ps.executeQuery()) {
                
                // Chuẩn bị câu lệnh chèn dữ liệu vào bảng mới
                String insertSQL = 
                    "INSERT INTO " + TRANSFER_HISTORY_TABLE + 
                    " (sender, receiver, material, amount, timestamp) VALUES (?, ?, ?, ?, ?)";
                
                try (PreparedStatement psInsert = conn.prepareStatement(insertSQL)) {
                    int count = 0;
                    
                    while (rs.next()) {
                        psInsert.setString(1, rs.getString("sender_name"));
                        psInsert.setString(2, rs.getString("receiver_name"));
                        psInsert.setString(3, rs.getString("material"));
                        psInsert.setInt(4, rs.getInt("amount"));
                        
                        // Xử lý timestamp: nếu là long thì chuyển thành chuỗi
                        long timestamp = rs.getLong("timestamp");
                        String timestampStr;
                        if (timestamp > 0) {
                            timestampStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));
                        } else {
                            timestampStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                        }
                        psInsert.setString(5, timestampStr);
                        
                        psInsert.addBatch();
                        count++;
                        
                        // Thực hiện theo lô để tránh quá tải
                        if (count % 100 == 0) {
                            psInsert.executeBatch();
                        }
                    }
                    
                    if (count % 100 != 0) {
                        psInsert.executeBatch();
                    }
                    
                    Storage.getStorage().getLogger().info("Đã di chuyển " + count + " bản ghi từ bảng cũ sang bảng mới.");
                }
            }
            
        } catch (SQLException e) {
            Storage.getStorage().getLogger().severe("Lỗi khi di chuyển dữ liệu: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Tạo bảng lịch sử giao dịch nếu chưa tồn tại
     * @return true nếu tạo thành công, false nếu thất bại
     */
    public static boolean createTransferHistoryTable() {
        Connection conn = null;
        PreparedStatement ps = null;
        
        try {
            conn = Storage.db.getConnection();
            
            // Kiểm tra xem bảng đã tồn tại chưa
            boolean tableExists = false;
            try (ResultSet rs = conn.getMetaData().getTables(null, null, TRANSFER_HISTORY_TABLE, null)) {
                tableExists = rs.next();
            }
            
            if (!tableExists) {
                String createTableSQL = 
                    "CREATE TABLE IF NOT EXISTS " + TRANSFER_HISTORY_TABLE + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "sender TEXT NOT NULL, " +
                    "receiver TEXT NOT NULL, " +
                    "material TEXT NOT NULL, " +
                    "amount INTEGER NOT NULL, " +
                    "timestamp TEXT NOT NULL)";
                
                ps = conn.prepareStatement(createTableSQL);
                ps.executeUpdate();
                return true;
            }
            
            return tableExists;
        } catch (SQLException e) {
            Storage.getStorage().getLogger().severe("Lỗi khi tạo bảng lịch sử chuyển giao: " + e.getMessage());
            return false;
        } finally {
            try {
                if (ps != null) ps.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                Storage.getStorage().getLogger().severe("Lỗi khi đóng kết nối: " + e.getMessage());
            }
        }
    }
    
    /**
     * Kiểm tra có thể chuyển tài nguyên hay không
     * @param player Người chơi cần kiểm tra
     * @return true nếu có thể chuyển
     */
    public static boolean canTransfer(Player player) {
        if (player == null) {
            return false;
        }
        
        // Kiểm tra quyền
        if (!player.hasPermission("storage.transfer")) {
            return false;
        }
        
        // Đọc cài đặt thời gian chờ từ config
        int cooldownSeconds = File.getConfig().getInt("transfer.cooldown", DEFAULT_COOLDOWN);
        
        // Kiểm tra thời gian chờ
        long now = System.currentTimeMillis();
        UUID playerUUID = player.getUniqueId();
        
        if (lastTransferTime.containsKey(playerUUID)) {
            long lastTime = lastTransferTime.get(playerUUID);
            long diff = now - lastTime;
            
            if (diff < TimeUnit.SECONDS.toMillis(cooldownSeconds)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Xử lý hiệu ứng chuyển giao thành công
     * @param sender Người gửi
     * @param receiver Người nhận
     * @param totalAmount Tổng số lượng đã chuyển
     */
    public static void playTransferEffects(Player sender, Player receiver, int totalAmount) {
        // Kiểm tra xem có bật hiệu ứng không
        if (!File.getConfig().getBoolean("transfer.effects_enabled", true) || 
            !File.getConfig().getBoolean("effects.enabled", true)) {
            return;
        }
        
        FileConfiguration config = File.getConfig();
        
        // Phát âm thanh
        String senderSound = config.getString("effects.transfer_success.sender_sound", "ENTITY_PLAYER_LEVELUP:0.5:1.0");
        String receiverSound = config.getString("effects.transfer_success.receiver_sound", "ENTITY_EXPERIENCE_ORB_PICKUP:0.5:1.0");
        
        if (senderSound != null && !senderSound.isEmpty()) {
            try {
                String[] parts = senderSound.split(":");
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(parts[0]);
                float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 0.5f;
                float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                
                sender.playSound(sender.getLocation(), sound, volume, pitch);
            } catch (Exception e) {
                // Bỏ qua lỗi
            }
        }
        
        if (receiverSound != null && !receiverSound.isEmpty() && receiver.isOnline()) {
            try {
                String[] parts = receiverSound.split(":");
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(parts[0]);
                float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 0.5f;
                float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                
                receiver.playSound(receiver.getLocation(), sound, volume, pitch);
            } catch (Exception e) {
                // Bỏ qua lỗi
            }
        }
        
        // Hiệu ứng hạt
        String senderParticle = config.getString("effects.transfer_success.sender_particle", "VILLAGER_HAPPY:0.3:0.3:0.3:0.05:10");
        String receiverParticle = config.getString("effects.transfer_success.receiver_particle", "COMPOSTER:0.3:0.3:0.3:0.05:10");
        
        // Kiểm tra số lượng lớn và sử dụng hiệu ứng đặc biệt
        if (totalAmount > 32) {
            String largeTransferSenderParticle = config.getString("effects.large_transfer.sender_particle");
            String largeTransferReceiverParticle = config.getString("effects.large_transfer.receiver_particle");
            
            if (largeTransferSenderParticle != null && !largeTransferSenderParticle.isEmpty()) {
                senderParticle = largeTransferSenderParticle;
            }
            
            if (largeTransferReceiverParticle != null && !largeTransferReceiverParticle.isEmpty()) {
                receiverParticle = largeTransferReceiverParticle;
            }
        }
        
        // Giới hạn số lượng hạt
        int maxParticleCount = config.getInt("settings.max_particle_count", 15);
        
        // Phát hiệu ứng hạt cho người gửi
        if (senderParticle != null && !senderParticle.isEmpty()) {
            try {
                String[] parts = senderParticle.split(":");
                org.bukkit.Particle particleType = org.bukkit.Particle.valueOf(parts[0]);
                
                double offsetX = parts.length > 1 ? Double.parseDouble(parts[1]) : 0.3;
                double offsetY = parts.length > 2 ? Double.parseDouble(parts[2]) : 0.3;
                double offsetZ = parts.length > 3 ? Double.parseDouble(parts[3]) : 0.3;
                double speed = parts.length > 4 ? Double.parseDouble(parts[4]) : 0.05;
                int count = parts.length > 5 ? Integer.parseInt(parts[5]) : 10;
                
                // Giới hạn số lượng hạt
                count = Math.min(count, maxParticleCount);
                
                sender.spawnParticle(particleType, sender.getLocation().clone().add(0, 1, 0), count, offsetX, offsetY, offsetZ, speed);
            } catch (Exception e) {
                // Bỏ qua lỗi
            }
        }
        
        // Phát hiệu ứng hạt cho người nhận nếu online
        if (receiverParticle != null && !receiverParticle.isEmpty() && receiver.isOnline()) {
            try {
                String[] parts = receiverParticle.split(":");
                org.bukkit.Particle particleType = org.bukkit.Particle.valueOf(parts[0]);
                
                double offsetX = parts.length > 1 ? Double.parseDouble(parts[1]) : 0.3;
                double offsetY = parts.length > 2 ? Double.parseDouble(parts[2]) : 0.3;
                double offsetZ = parts.length > 3 ? Double.parseDouble(parts[3]) : 0.3;
                double speed = parts.length > 4 ? Double.parseDouble(parts[4]) : 0.05;
                int count = parts.length > 5 ? Integer.parseInt(parts[5]) : 10;
                
                // Giới hạn số lượng hạt
                count = Math.min(count, maxParticleCount);
                
                receiver.spawnParticle(particleType, receiver.getLocation().clone().add(0, 1, 0), count, offsetX, offsetY, offsetZ, speed);
            } catch (Exception e) {
                // Bỏ qua lỗi
            }
        }
    }
    
    /**
     * Xử lý hiệu ứng chuyển giao thất bại
     * @param player Người chơi gặp lỗi
     */
    public static void playTransferFailEffect(Player player) {
        // Kiểm tra xem có bật hiệu ứng không
        if (!File.getConfig().getBoolean("transfer.effects_enabled", true) || 
            !File.getConfig().getBoolean("effects.enabled", true)) {
            return;
        }
        
        String soundConfig = File.getConfig().getString("effects.transfer_fail.sound", "ENTITY_VILLAGER_NO:1.0:1.0");
        
        if (soundConfig != null && !soundConfig.isEmpty()) {
            try {
                String[] parts = soundConfig.split(":");
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(parts[0]);
                float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (Exception e) {
                // Bỏ qua lỗi
            }
        }
    }
    
    /**
     * Lớp lưu trữ thông tin chuyển giao
     */
    public static class TransferRecord {
        private int id;
        private String sender;
        private String receiver;
        private String material;
        private int amount;
        private String timestamp;
        
        public TransferRecord(int id, String sender, String receiver, String material, int amount, String timestamp) {
            this.id = id;
            this.sender = sender;
            this.receiver = receiver;
            this.material = material;
            this.amount = amount;
            this.timestamp = timestamp;
        }
        
        public int getId() {
            return id;
        }
        
        public String getSender() {
            return sender;
        }
        
        public String getReceiver() {
            return receiver;
        }
        
        public String getMaterial() {
            return material;
        }
        
        public int getAmount() {
            return amount;
        }
        
        public String getTimestamp() {
            return timestamp;
        }
        
        public String getMaterialDisplayName() {
            return MineManager.getMaterialDisplayName(material);
        }
        
        @Override
        public String toString() {
            String materialName = getMaterialDisplayName();
            String type = "";
            
            return timestamp + " - " + amount + "x " + materialName + " từ " + sender + " đến " + receiver;
        }
    }
    
    /**
     * Lấy tổng số lịch sử giao dịch của người chơi
     * @param playerName Tên người chơi
     * @return Tổng số giao dịch
     */
    public static int getTotalTransferHistoryCount(String playerName) {
        int count = 0;
        
        // Kiểm tra bảng tồn tại
        if (!isTableExists()) {
            // Tạo bảng nếu chưa tồn tại
            if (!createTransferHistoryTable()) {
                Storage.getStorage().getLogger().warning("Không thể tạo bảng lịch sử giao dịch. Trả về số lượng 0.");
                return 0;
            }
        }
        
        // Truy vấn SQL để đếm số lịch sử giao dịch
        String sql = "SELECT COUNT(*) as total FROM " + TRANSFER_HISTORY_TABLE + " WHERE sender = ? OR receiver = ?";
        
        try (Connection conn = Storage.db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, playerName);
            ps.setString(2, playerName);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    count = rs.getInt("total");
                }
            }
        } catch (SQLException e) {
            Storage.getStorage().getLogger().severe("Lỗi khi đếm lịch sử giao dịch: " + e.getMessage());
            e.printStackTrace();
        }
        
        return count;
    }
    
    /**
     * Lấy lịch sử giao dịch của người chơi (cả gửi và nhận) với phân trang
     * @param playerName Tên người chơi
     * @param page Số trang (bắt đầu từ 0)
     * @param itemsPerPage Số lượng mục trên mỗi trang
     * @return Danh sách lịch sử giao dịch theo trang
     */
    public static List<TransferHistory> getPlayerTransferHistoryPaged(String playerName, int page, int itemsPerPage) {
        int offset = page * itemsPerPage;
        List<TransferHistory> history = new ArrayList<>();
        
        // Kiểm tra bảng tồn tại
        if (!isTableExists()) {
            // Tạo bảng nếu chưa tồn tại
            if (!createTransferHistoryTable()) {
                Storage.getStorage().getLogger().warning("Không thể tạo bảng lịch sử giao dịch. Trả về danh sách trống.");
                return history;
            }
        }
        
        // Truy vấn SQL để lấy lịch sử giao dịch với phân trang
        String sql = "SELECT * FROM " + TRANSFER_HISTORY_TABLE + " WHERE sender = ? OR receiver = ? ORDER BY timestamp DESC LIMIT ? OFFSET ?";
        
        try (Connection conn = Storage.db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, playerName);
            ps.setString(2, playerName);
            ps.setInt(3, itemsPerPage);
            ps.setInt(4, offset);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TransferHistory transfer = new TransferHistory(
                        rs.getInt("id"),
                        rs.getString("sender"),
                        rs.getString("receiver"),
                        rs.getString("material"),
                        rs.getInt("amount"),
                        parseTimestamp(rs.getString("timestamp"))
                    );
                    
                    history.add(transfer);
                }
            }
        } catch (SQLException e) {
            Storage.getStorage().getLogger().severe("Lỗi khi lấy lịch sử giao dịch: " + e.getMessage());
            e.printStackTrace();
        }
        
        return history;
    }
    
    /**
     * Lấy lịch sử giao dịch của người chơi (cả gửi và nhận) với giới hạn số lượng
     * @param playerName Tên người chơi
     * @param page Số trang (bắt đầu từ 0)
     * @param itemsPerPage Số lượng mục trên mỗi trang
     * @param maxItems Số lượng tối đa mục sẽ xem xét
     * @return Danh sách lịch sử giao dịch theo trang nhưng giới hạn tối đa chỉ sẽ đến maxItems
     */
    public static List<TransferHistory> getPlayerTransferHistoryLimited(String playerName, int page, int itemsPerPage, int maxItems) {
        int offset = page * itemsPerPage;
        
        // Đảm bảo offset không vượt quá giới hạn tối đa
        if (offset >= maxItems) {
            offset = Math.max(0, maxItems - itemsPerPage);
        }
        
        List<TransferHistory> history = new ArrayList<>();
        
        // Kiểm tra bảng tồn tại
        if (!isTableExists()) {
            // Tạo bảng nếu chưa tồn tại
            if (!createTransferHistoryTable()) {
                Storage.getStorage().getLogger().warning("Không thể tạo bảng lịch sử giao dịch. Trả về danh sách trống.");
                return history;
            }
        }
        
        // Truy vấn SQL để lấy lịch sử giao dịch với giới hạn tối đa
        String sql = "SELECT * FROM " + TRANSFER_HISTORY_TABLE + " WHERE (sender = ? OR receiver = ?) ORDER BY timestamp DESC LIMIT ? OFFSET ?";
        
        try (Connection conn = Storage.db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, playerName);
            ps.setString(2, playerName);
            ps.setInt(3, itemsPerPage);
            ps.setInt(4, offset);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TransferHistory transfer = new TransferHistory(
                        rs.getInt("id"),
                        rs.getString("sender"),
                        rs.getString("receiver"),
                        rs.getString("material"),
                        rs.getInt("amount"),
                        parseTimestamp(rs.getString("timestamp"))
                    );
                    
                    history.add(transfer);
                }
            }
        } catch (SQLException e) {
            Storage.getStorage().getLogger().severe("Lỗi khi lấy lịch sử giao dịch giới hạn: " + e.getMessage());
            e.printStackTrace();
        }
        
        return history;
    }
    
    /**
     * Lấy lịch sử giao dịch giữa hai người chơi
     * @param playerName1 Tên người chơi thứ nhất
     * @param playerName2 Tên người chơi thứ hai
     * @param page Trang hiện tại (bắt đầu từ 0)
     * @param pageSize Số lượng giao dịch trên mỗi trang
     * @return Danh sách lịch sử giao dịch
     */
    public static List<TransferHistory> getTransferHistoryBetweenPlayers(String playerName1, String playerName2, int page, int pageSize) {
        List<TransferHistory> history = new ArrayList<>();
        
        // Kiểm tra bảng tồn tại
        if (!isTableExists()) {
            return history;
        }
        
        // Tính offset dựa trên trang và kích thước trang
        int offset = page * pageSize;
        
        // Truy vấn SQL để lấy lịch sử giao dịch giữa hai người chơi
        String sql = "SELECT * FROM " + TRANSFER_HISTORY_TABLE + " WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?) ORDER BY timestamp DESC LIMIT ? OFFSET ?";
        
        try (Connection conn = Storage.db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, playerName1);
            ps.setString(2, playerName2);
            ps.setString(3, playerName2);
            ps.setString(4, playerName1);
            ps.setInt(5, pageSize);
            ps.setInt(6, offset);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TransferHistory transfer = new TransferHistory(
                        rs.getInt("id"),
                        rs.getString("sender"),
                        rs.getString("receiver"),
                        rs.getString("material"),
                        rs.getInt("amount"),
                        parseTimestamp(rs.getString("timestamp"))
                    );
                    
                    history.add(transfer);
                }
            }
        } catch (SQLException e) {
            Storage.getStorage().getLogger().severe("Lỗi khi lấy lịch sử giao dịch: " + e.getMessage());
            e.printStackTrace();
        }
        
        return history;
    }
    
    /**
     * Đếm tổng số giao dịch giữa hai người chơi
     * @param playerName1 Tên người chơi thứ nhất
     * @param playerName2 Tên người chơi thứ hai
     * @return Tổng số giao dịch
     */
    public static int countTransferHistoryBetweenPlayers(String playerName1, String playerName2) {
        // Kiểm tra bảng tồn tại
        if (!isTableExists()) {
            return 0;
        }
        
        // Truy vấn SQL để đếm tổng số giao dịch giữa hai người chơi
        String sql = "SELECT COUNT(*) as total FROM " + TRANSFER_HISTORY_TABLE + " WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)";
        
        try (Connection conn = Storage.db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, playerName1);
            ps.setString(2, playerName2);
            ps.setString(3, playerName2);
            ps.setString(4, playerName1);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total");
                }
            }
        } catch (SQLException e) {
            Storage.getStorage().getLogger().severe("Lỗi khi đếm lịch sử giao dịch: " + e.getMessage());
            e.printStackTrace();
        }
        
        return 0;
    }

    /**
     * Lấy danh sách tin nhắn lịch sử giao dịch của người chơi
     * @param playerName Tên người chơi
     * @param limit Số lượng tối đa lịch sử trả về
     * @return Danh sách tin nhắn lịch sử
     */
    public static List<String> getTransferHistoryMessages(String playerName, int limit) {
        List<String> messages = new ArrayList<>();
        
        // Kiểm tra bảng tồn tại
        if (!isTableExists()) {
            messages.add(Chat.colorize("&cKhông tìm thấy dữ liệu lịch sử giao dịch."));
            return messages;
        }
        
        List<TransferHistory> history = getPlayerTransferHistory(playerName, limit);
        
        if (history.isEmpty()) {
            messages.add(Chat.colorize("&eBạn chưa có giao dịch nào."));
            return messages;
        }
        
        for (TransferHistory transfer : history) {
            String materialName = File.getConfig().getString("items." + transfer.getMaterial(), transfer.getMaterial().split(";")[0]);
            
            // Định dạng thời gian
            String time = DATE_FORMAT.format(transfer.getDate());
            
            // Kiểm tra xem người chơi là người gửi hay người nhận
            if (transfer.getSenderName().equalsIgnoreCase(playerName)) {
                // Người chơi là người gửi
                messages.add(Chat.colorize(
                    "&8[&e" + time + "&8] &fBạn đã chuyển &a" + transfer.getAmount() + " " + materialName + 
                    " &fcho &e" + transfer.getReceiverName()
                ));
            } else {
                // Người chơi là người nhận
                messages.add(Chat.colorize(
                    "&8[&e" + time + "&8] &fBạn đã nhận &a" + transfer.getAmount() + " " + materialName + 
                    " &ftừ &e" + transfer.getSenderName()
                ));
            }
        }
        
        return messages;
    }

    /**
     * Ghi một giao dịch chuyển khoáng sản vào lịch sử một cách bất đồng bộ
     */
    public static void recordTransferAsync(String sender, String receiver, String material, int amount) {
        if (sender == null || receiver == null || material == null || amount <= 0) {
            return;
        }
        
        // Lưu dữ liệu cần thiết cho task bất đồng bộ
        final String finalSender = sender;
        final String finalReceiver = receiver;
        final String finalMaterial = material;
        final int finalAmount = amount;
        
        // Thực hiện ghi lịch sử trong luồng bất đồng bộ
        Bukkit.getScheduler().runTaskAsynchronously(Storage.getStorage(), () -> {
            Connection conn = null;
            PreparedStatement ps = null;
            
            try {
                // Đảm bảo bảng lịch sử tồn tại
                if (!isTableExists()) {
                    createTransferHistoryTable();
                }
                
                conn = Storage.db.getConnection();
                if (conn == null) {
                    Storage.getStorage().getLogger().severe("Không thể kết nối đến cơ sở dữ liệu để ghi lịch sử chuyển khoản");
                    return;
                }
                
                // Chuẩn bị timestamp
                String timestamp = formatTimestamp(System.currentTimeMillis());
                
                // Thêm giao dịch vào bảng lịch sử
                ps = conn.prepareStatement(
                    "INSERT INTO transfer_history (sender, receiver, material, amount, timestamp) VALUES (?, ?, ?, ?, ?)"
                );
                ps.setString(1, finalSender);
                ps.setString(2, finalReceiver);
                ps.setString(3, finalMaterial);
                ps.setInt(4, finalAmount);
                ps.setString(5, timestamp);
                ps.executeUpdate();
                
                // Nếu bật debug mode, ghi log
                if (Storage.getStorage().getConfig().getBoolean("settings.debug_mode", false)) {
                    Storage.getStorage().getLogger().info(
                        "Đã ghi lịch sử chuyển khoản: " + finalSender + " -> " + finalReceiver + 
                        ", " + finalMaterial + " x" + finalAmount + " (bất đồng bộ)"
                    );
                }
                
            } catch (SQLException e) {
                Storage.getStorage().getLogger().log(Level.SEVERE, "Lỗi SQL khi ghi lịch sử chuyển khoản: " + e.getMessage(), e);
            } finally {
                try {
                    if (ps != null) ps.close();
                    if (conn != null) Storage.db.returnConnection(conn);
                } catch (SQLException e) {
                    Storage.getStorage().getLogger().log(Level.SEVERE, "Lỗi khi đóng kết nối: " + e.getMessage(), e);
                }
            }
        });
    }
} 