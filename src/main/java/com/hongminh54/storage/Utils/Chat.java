package com.hongminh54.storage.Utils;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;

/**
 * Lớp tiện ích để xử lý và định dạng văn bản trong plugin.
 * <p>
 * Cung cấp các phương thức để:
 * <ul>
 *     <li>Chuyển đổi mã màu</li>
 *     <li>Định dạng tin nhắn</li>
 *     <li>Xử lý danh sách tin nhắn</li>
 * </ul>
 */
public class Chat {

    /** Mẫu regex để nhận diện mã hex color */
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    /**
     * Chuyển đổi mã màu trong chuỗi (hỗ trợ cả mã màu legacy & và mã màu hex)
     * và thêm tiền tố từ config
     * 
     * @param message Chuỗi cần chuyển đổi
     * @return Chuỗi đã được chuyển đổi màu và thêm tiền tố
     */
    public static @NotNull String colorize(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        
        return ChatColor.translateAlternateColorCodes('&', File.getConfig().getString("prefix") + " " + message);
    }

    /**
     * Chuyển đổi mã màu trong chuỗi (chỉ hỗ trợ mã màu legacy &, không thêm tiền tố)
     * 
     * @param message Chuỗi cần chuyển đổi
     * @return Chuỗi đã được chuyển đổi màu
     */
    public static @NotNull String colorizewp(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Chuyển đổi mã màu cho mảng chuỗi
     * 
     * @param messages Mảng chuỗi cần chuyển đổi
     * @return Danh sách chuỗi đã được chuyển đổi màu
     */
    public static @NotNull List<String> colorize(String... messages) {
        return Arrays.stream(messages).map(Chat::colorize).collect(Collectors.toList());
    }

    /**
     * Chuyển đổi mã màu cho danh sách chuỗi (không thêm tiền tố)
     * 
     * @param message Danh sách chuỗi cần chuyển đổi
     * @return Danh sách chuỗi đã được chuyển đổi màu
     */
    public static @NotNull List<String> colorizewp(List<String> message) {
        return message.stream().map(Chat::colorizewp).collect(Collectors.toList());
    }

    /**
     * Loại bỏ tất cả mã màu khỏi chuỗi
     * 
     * @param message Chuỗi cần loại bỏ mã màu
     * @return Chuỗi không có mã màu
     */
    public static String stripColor(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        
        return ChatColor.stripColor(colorizewp(message));
    }
    
    /**
     * Định dạng tin nhắn với tiền tố từ config
     * 
     * @param message Tin nhắn cần định dạng
     * @return Tin nhắn đã định dạng với tiền tố
     */
    public static String formatWithPrefix(String message) {
        String prefix = File.getConfig().getString("prefix", "");
        if (prefix.isEmpty()) {
            return colorizewp(message);
        } else {
            return colorizewp(prefix + " " + message);
        }
    }
    
    /**
     * Thay thế placeholder trong chuỗi
     * 
     * @param message Chuỗi cần thay thế
     * @param placeholder Placeholder cần tìm
     * @param replacement Giá trị thay thế
     * @return Chuỗi đã được thay thế
     */
    public static String replacePlaceholder(String message, String placeholder, String replacement) {
        if (message == null || placeholder == null || replacement == null) {
            return message;
        }
        
        return message.replace(placeholder, replacement);
    }
}
