package com.hongminh54.storage.Database;

import java.util.logging.Level;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Lớp tiện ích để xử lý và ghi log các lỗi liên quan đến cơ sở dữ liệu.
 * <p>
 * Cung cấp các phương thức để ghi log lỗi với các mức độ khác nhau
 * và các loại lỗi liên quan đến cơ sở dữ liệu.
 */
public class Error {
    
    /**
     * Ghi log lỗi khi thực thi câu lệnh SQL
     * 
     * @param plugin Plugin đang thực thi
     * @param ex Lỗi phát sinh
     */
    public static void execute(@NotNull JavaPlugin plugin, Exception ex) {
        plugin.getLogger().log(Level.SEVERE, "Couldn't execute SQL statement: ", ex);
    }

    /**
     * Ghi log lỗi khi đóng kết nối cơ sở dữ liệu
     * 
     * @param plugin Plugin đang thực thi
     * @param ex Lỗi phát sinh
     */
    public static void close(@NotNull JavaPlugin plugin, Exception ex) {
        plugin.getLogger().log(Level.SEVERE, "Failed to close SQL connection: ", ex);
    }
    
    /**
     * Ghi log lỗi khi không thể kết nối đến cơ sở dữ liệu
     * 
     * @param plugin Plugin đang thực thi
     * @param ex Lỗi phát sinh
     */
    public static void connection(@NotNull JavaPlugin plugin, Exception ex) {
        plugin.getLogger().log(Level.SEVERE, "Failed to establish SQL connection: ", ex);
    }
    
    /**
     * Ghi log lỗi với mức độ tùy chỉnh
     * 
     * @param plugin Plugin đang thực thi
     * @param level Mức độ lỗi
     * @param message Thông điệp lỗi
     * @param ex Lỗi phát sinh
     */
    public static void log(@NotNull JavaPlugin plugin, Level level, String message, Exception ex) {
        plugin.getLogger().log(level, message, ex);
    }
    
    /**
     * Ghi log lỗi với mức độ WARNING
     * 
     * @param plugin Plugin đang thực thi
     * @param message Thông điệp lỗi
     * @param ex Lỗi phát sinh (có thể null)
     */
    public static void warning(@NotNull JavaPlugin plugin, String message, Exception ex) {
        if (ex != null) {
            plugin.getLogger().log(Level.WARNING, message, ex);
        } else {
            plugin.getLogger().warning(message);
        }
    }
    
    /**
     * Ghi log lỗi với mức độ INFO
     * 
     * @param plugin Plugin đang thực thi
     * @param message Thông điệp lỗi
     */
    public static void info(@NotNull JavaPlugin plugin, String message) {
        plugin.getLogger().info(message);
    }
}
