package com.hongminh54.storage.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;

/**
 * Lớp tiện ích để quản lý cooldown cho các hoạt động trong plugin.
 * <p>
 * Cung cấp cơ chế để theo dõi và quản lý thời gian chờ giữa các lần thực hiện
 * hành động, giúp kiểm soát tần suất sử dụng các tính năng.
 */
public class Cooldown {

    /** Lưu trữ cooldown dựa trên Location (chức năng gốc) */
    private static final Map<Location, Integer> cooldown = new HashMap<>();
    
    /** Lưu trữ cooldown nâng cao dựa trên UUID và key */
    private static final Map<String, Map<UUID, Long>> advancedCooldowns = new ConcurrentHashMap<>();

    /**
     * Đặt thời gian cooldown cho một vị trí cụ thể
     *
     * @param location Vị trí cần đặt cooldown
     * @param time Thời gian cooldown
     */
    public static void setCooldown(Location location, int time) {
        if (time < 1) {
            cooldown.remove(location);
        } else {
            cooldown.put(location, time);
        }
    }
    
    /**
     * Lấy thời gian cooldown còn lại của một vị trí
     *
     * @param location Vị trí cần kiểm tra
     * @return Thời gian cooldown còn lại
     */
    public static int getCooldown(Location location) {
        return cooldown.getOrDefault(location, 0);
    }
    
    /**
     * Giảm thời gian cooldown của tất cả các vị trí
     * Thường được gọi định kỳ để giảm thời gian chờ
     */
    public static void decreaseCooldowns() {
        cooldown.entrySet().removeIf(entry -> entry.getValue() <= 1);
        cooldown.replaceAll((loc, val) -> val - 1);
    }

    // ===== Các phương thức cooldown nâng cao dựa trên UUID và key =====
    
    /**
     * Kiểm tra xem người chơi có đang trong thời gian cooldown không
     * 
     * @param uuid Định danh người chơi
     * @param key Khóa định danh cho cooldown
     * @param cooldownTimeInSeconds Thời gian cooldown (tính bằng giây)
     * @return true nếu cooldown vẫn còn hiệu lực, false nếu đã hết
     */
    public static boolean hasCooldown(UUID uuid, String key, int cooldownTimeInSeconds) {
        if (!advancedCooldowns.containsKey(key)) {
            advancedCooldowns.put(key, new HashMap<>());
        }
        
        Map<UUID, Long> cooldownMap = advancedCooldowns.get(key);
        long currentTime = System.currentTimeMillis() / 1000;
        
        if (cooldownMap.containsKey(uuid)) {
            long cooldownTime = cooldownMap.get(uuid);
            
            if (currentTime - cooldownTime >= cooldownTimeInSeconds) {
                // Cooldown đã hết, cập nhật thời gian mới
                cooldownMap.put(uuid, currentTime);
                return false;
            } else {
                // Cooldown vẫn còn hiệu lực
                return true;
            }
        } else {
            // Không có cooldown trước đó, thêm mới
            cooldownMap.put(uuid, currentTime);
            return false;
        }
    }
    
    /**
     * Lấy thời gian còn lại của cooldown (tính bằng giây)
     * 
     * @param uuid Định danh người chơi
     * @param key Khóa định danh cho cooldown
     * @param cooldownTimeInSeconds Tổng thời gian cooldown (giây)
     * @return Thời gian còn lại (giây), hoặc 0 nếu đã hết cooldown
     */
    public static int getRemainingCooldown(UUID uuid, String key, int cooldownTimeInSeconds) {
        if (!advancedCooldowns.containsKey(key)) {
            return 0;
        }
        
        Map<UUID, Long> cooldownMap = advancedCooldowns.get(key);
        if (!cooldownMap.containsKey(uuid)) {
            return 0;
        }
        
        long cooldownTime = cooldownMap.get(uuid);
        long currentTime = System.currentTimeMillis() / 1000;
        long elapsedTime = currentTime - cooldownTime;
        
        if (elapsedTime >= cooldownTimeInSeconds) {
            return 0;
        } else {
            return (int)(cooldownTimeInSeconds - elapsedTime);
        }
    }
    
    /**
     * Đặt lại cooldown cho người chơi
     * 
     * @param uuid Định danh người chơi
     * @param key Khóa định danh cho cooldown
     */
    public static void resetCooldown(UUID uuid, String key) {
        if (!advancedCooldowns.containsKey(key)) {
            advancedCooldowns.put(key, new HashMap<>());
        }
        
        advancedCooldowns.get(key).put(uuid, System.currentTimeMillis() / 1000);
    }
    
    /**
     * Xóa cooldown cho người chơi
     * 
     * @param uuid Định danh người chơi
     * @param key Khóa định danh cho cooldown
     */
    public static void removeCooldown(UUID uuid, String key) {
        if (advancedCooldowns.containsKey(key)) {
            advancedCooldowns.get(key).remove(uuid);
        }
    }
    
    /**
     * Xóa tất cả cooldown của người chơi
     * 
     * @param uuid Định danh người chơi
     */
    public static void clearAllCooldowns(UUID uuid) {
        for (Map<UUID, Long> cooldownMap : advancedCooldowns.values()) {
            cooldownMap.remove(uuid);
        }
    }
}
