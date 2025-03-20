package com.hongminh54.storage.Utils;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;

/**
 * Lớp xử lý text trong GUI, đảm bảo không có prefix
 */
public class GUIText {

    /**
     * Chuyển đổi mã màu trong chuỗi
     * @param text Chuỗi cần chuyển đổi
     * @return Chuỗi đã được định dạng
     */
    public static @NotNull String format(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }
    
    /**
     * Chuyển đổi mã màu trong danh sách chuỗi
     * @param lines Danh sách chuỗi cần chuyển đổi
     * @return Danh sách chuỗi đã được định dạng
     */
    public static @NotNull List<String> format(List<String> lines) {
        List<String> result = new ArrayList<>();
        if (lines == null) return result;
        
        for (String line : lines) {
            result.add(format(line));
        }
        return result;
    }
    
    /**
     * Thay thế các placeholder và chuyển đổi mã màu
     * @param text Chuỗi cần xử lý
     * @param replacements Mảng các cặp giá trị thay thế (placeholder, value)
     * @return Chuỗi đã được xử lý
     */
    public static @NotNull String replace(String text, String... replacements) {
        if (text == null) return "";
        
        String result = text;
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                result = result.replace(replacements[i], replacements[i + 1]);
            }
        }
        
        return format(result);
    }
    
    /**
     * Thay thế các placeholder và chuyển đổi mã màu cho danh sách chuỗi
     * @param lines Danh sách chuỗi cần xử lý
     * @param replacements Mảng các cặp giá trị thay thế (placeholder, value)
     * @return Danh sách chuỗi đã được xử lý
     */
    public static @NotNull List<String> replace(List<String> lines, String... replacements) {
        List<String> result = new ArrayList<>();
        if (lines == null) return result;
        
        for (String line : lines) {
            result.add(replace(line, replacements));
        }
        
        return result;
    }
} 