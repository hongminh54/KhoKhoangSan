package com.hongminh54.storage.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

import org.jetbrains.annotations.NotNull;

import com.hongminh54.storage.Storage;


public abstract class Database {
    public String table = "PlayerData";
    Storage main;
    Connection connection;
    
    // Connection pool để tối ưu hiệu suất kết nối
    private final ConcurrentLinkedQueue<Connection> connectionPool = new ConcurrentLinkedQueue<>();
    private static final int MAX_POOL_SIZE = 5;

    public Database(Storage instance) {
        main = instance;
    }

    public abstract Connection getSQLConnection();

    public abstract void load();

    public void initialize() {
        connection = getSQLConnection();
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT * FROM " + table + " WHERE player = ?");
            ps.setString(1, "init");
            ResultSet rs = ps.executeQuery();
            close(ps, rs);
        } catch (SQLException ex) {
            Storage.getStorage().getLogger().log(Level.SEVERE, "Unable to retrieve connection", ex);
        }
    }
    
    /**
     * Lấy kết nối từ pool hoặc tạo mới nếu pool trống
     * @return Kết nối SQL
     */
    public Connection getConnection() {
        Connection conn = connectionPool.poll();
        if (conn == null) {
            return getSQLConnection();
        }
        
        try {
            if (conn.isClosed()) {
                return getSQLConnection();
            }
        } catch (SQLException e) {
            return getSQLConnection();
        }
        
        return conn;
    }
    
    /**
     * Trả kết nối về pool thay vì đóng
     * @param conn Kết nối cần trả về
     */
    public void returnConnection(Connection conn) {
        if (conn == null) return;
        
        try {
            if (!conn.isClosed() && !conn.isReadOnly() && connectionPool.size() < MAX_POOL_SIZE) {
                connectionPool.offer(conn);
            } else {
                conn.close();
            }
        } catch (SQLException e) {
            try {
                conn.close();
            } catch (SQLException ignored) {}
        }
    }

    // These are the methods you can use to get things out of your database. You of course can make new ones to return different things in the database.
    // This returns the number of people the player killed.
    public PlayerData getData(String player) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("SELECT * FROM " + table + " WHERE player = '" + player + "';");
            rs = ps.executeQuery();
            if (rs.next()) {
                String statsData = "{}";
                try {
                    statsData = rs.getString("statsData");
                    if (statsData == null) statsData = "{}";
                } catch (SQLException e) {
                    // Cột có thể chưa tồn tại trong bảng cũ
                    statsData = "{}";
                }
                return new PlayerData(rs.getString("player"), rs.getString("data"), rs.getInt("max"), statsData);
            }
        } catch (SQLException ex) {
            Storage.getStorage().getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            try {
                if (ps != null) ps.close();
                if (rs != null) rs.close();
                if (conn != null) returnConnection(conn);
            } catch (SQLException ex) {
                Storage.getStorage().getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
        return null;
    }

    public void createTable(@NotNull PlayerData playerData) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("INSERT INTO " + table + " (player,data,max,statsData) VALUES(?,?,?,?)");
            ps.setString(1, playerData.getPlayer());
            ps.setString(2, playerData.getData());
            ps.setInt(3, playerData.getMax());
            ps.setString(4, playerData.getStatsData());
            ps.executeUpdate();
        } catch (SQLException ex) {
            Storage.getStorage().getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            try {
                if (ps != null)
                    ps.close();
                if (conn != null)
                    returnConnection(conn);
            } catch (SQLException ex) {
                Storage.getStorage().getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
    }

    public void updateTable(@NotNull PlayerData playerData) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("UPDATE " + table + " SET data = ?, max = ?, statsData = ? " +
                    "WHERE player = ?");
            conn.setAutoCommit(false);
            ps.setString(1, playerData.getData());
            ps.setInt(2, playerData.getMax());
            ps.setString(3, playerData.getStatsData());
            ps.setString(4, playerData.getPlayer());
            ps.addBatch();
            ps.executeBatch();
            conn.commit();
        } catch (SQLException ex) {
            Storage.getStorage().getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            try {
                if (ps != null)
                    ps.close();
                if (conn != null)
                    returnConnection(conn);
            } catch (SQLException ex) {
                Storage.getStorage().getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
    }

    public void deleteData(String player) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("DELETE FROM " + table + " WHERE player = ?");
            ps.setString(1, player);
            ps.executeUpdate();
        } catch (SQLException ex) {
            Storage.getStorage().getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            try {
                if (ps != null)
                    ps.close();
                if (conn != null)
                    returnConnection(conn);
            } catch (SQLException ex) {
                Storage.getStorage().getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
    }

    public void close(PreparedStatement ps, ResultSet rs) {
        try {
            if (ps != null) ps.close();
            if (rs != null) rs.close();
        } catch (SQLException ex) {
            Error.close(Storage.getStorage(), ex);
        }
    }
    
    /**
     * Đóng kết nối database khi plugin tắt
     */
    public void closeConnection() {
        try {
            // Đóng tất cả các kết nối trong pool
            for (Connection conn : connectionPool) {
                if (conn != null && !conn.isClosed()) {
                    conn.close();
                }
            }
            connectionPool.clear();
            
            // Đóng kết nối chính
            if (connection != null && !connection.isClosed()) {
                connection.close();
                Storage.getStorage().getLogger().info("Đã đóng kết nối đến cơ sở dữ liệu.");
            }
        } catch (SQLException ex) {
            Storage.getStorage().getLogger().log(Level.SEVERE, "Lỗi khi đóng kết nối cơ sở dữ liệu", ex);
        }
    }
}
