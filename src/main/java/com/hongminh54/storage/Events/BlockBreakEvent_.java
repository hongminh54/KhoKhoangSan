package com.hongminh54.storage.Events;

/**
 * Lớp này xử lý sự kiện khi người chơi đào khối trong trường hợp toggle của người chơi là FALSE.
 * Lớp BlockBreak.java xử lý sự kiện khi toggle của người chơi là TRUE.
 * 
 * Chú ý: Cả hai lớp này KHÔNG được xử lý cùng một sự kiện đào khối vì sẽ gây ra việc
 * tài nguyên được thêm hai lần vào kho của người chơi. Kiểm tra MineManager.toggle
 * đã được thêm để tránh xung đột này.
 */

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import com.hongminh54.storage.Manager.MineManager;
import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.CacheManager;
import com.hongminh54.storage.Utils.Chat;
import com.hongminh54.storage.Utils.File;
import com.hongminh54.storage.Utils.StatsManager;

public class BlockBreakEvent_ implements Listener {
    
    // Cache để tránh thông báo kho đầy quá thường xuyên
    private static final ConcurrentHashMap<String, Long> storageFullNotificationCache = new ConcurrentHashMap<>();
    
    // Cooldown giữa các lần đào (milliseconds)
    private static long BREAK_COOLDOWN = 20;
    // Giới hạn số lượng block có thể đào trong khoảng thời gian
    private static int MAX_BREAKS = 25;
    // Thời gian reset bộ đếm (giây)
    private static int RESET_INTERVAL = 3;
    // Thời gian giữa các thông báo kho đầy (giây)
    private static final long NOTIFICATION_COOLDOWN = TimeUnit.SECONDS.toMillis(30);
    
    // Cấu hình hiệu ứng
    private boolean effectsEnabled;
    private int maxParticleCount;
    private boolean autoPickupEnabled;
    private boolean cancelDropEnabled;
    private boolean sendMessages;
    
    // Cache thời gian hiệu ứng cuối của mỗi người chơi
    private static final ConcurrentHashMap<String, Long> lastParticleEffectTime = new ConcurrentHashMap<>();
    // Khoảng thời gian tối thiểu giữa các hiệu ứng (milliseconds)
    private static final long PARTICLE_EFFECT_COOLDOWN = 800;
    
    // Thêm cache để kiểm soát tần suất đào block
    private static final ConcurrentHashMap<UUID, Long> lastBreakTime = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> breakCount = new ConcurrentHashMap<>();
    
    // Constructor để khởi tạo cấu hình ngay khi class được tạo
    public BlockBreakEvent_() {
        loadConfig();
        // Đăng ký task dọn dẹp cache
        scheduleCacheCleanup();
    }
    
    // Tải lại cấu hình khi admin reload plugin
    public void loadConfig() {
        FileConfiguration config = File.getConfig();
        
        effectsEnabled = config.getBoolean("effects.enabled", true);
        maxParticleCount = config.getInt("settings.max_particle_count", 15);
        autoPickupEnabled = config.getBoolean("settings.auto_pickup", true);
        cancelDropEnabled = config.getBoolean("settings.cancel_drop", true);
        sendMessages = config.getBoolean("settings.send_messages", true);
        
        // Đọc cài đặt cache từ config
        BREAK_COOLDOWN = config.getInt("cache.cooldown.block_break", 20);
        boolean limitEnabled = config.getBoolean("cache.cooldown.limit_enabled", true);
        if (limitEnabled) {
            MAX_BREAKS = config.getInt("cache.cooldown.max_breaks", 25);
            RESET_INTERVAL = config.getInt("cache.cooldown.reset_interval", 3);
        } else {
            // Nếu giới hạn bị tắt, đặt giá trị rất cao để không bao giờ đạt đến
            MAX_BREAKS = Integer.MAX_VALUE;
            RESET_INTERVAL = Integer.MAX_VALUE;
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        // Các kiểm tra quan trọng cần xử lý ngay trong luồng chính
        if (event.isCancelled()) {
            return;
        }
        
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        Block block = event.getBlock();
        
        // Kiểm tra xem toggle có được bật không - nếu được bật thì BlockBreak.java sẽ xử lý
        // Điều này ngăn chặn việc xử lý khối đã đào hai lần
        if (MineManager.toggle.containsKey(player) && MineManager.toggle.get(player)) {
            return;
        }
        
        // Kiểm tra cooldown để tránh lag server từ việc đào quá nhanh
        long now = System.currentTimeMillis();
        if (lastBreakTime.containsKey(playerUUID)) {
            long lastTime = lastBreakTime.get(playerUUID);
            long elapsed = now - lastTime;
            if (elapsed < BREAK_COOLDOWN) {
                // Không hủy sự kiện nếu thời gian chênh lệch quá nhỏ, chỉ cập nhật thời gian
                if (elapsed < BREAK_COOLDOWN / 2) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        
        // Cập nhật thời gian đào gần nhất
        lastBreakTime.put(playerUUID, now);
        
        // Tăng số đếm block đã phá để kiểm soát tốc độ đào
        int count = breakCount.getOrDefault(playerUUID, 0) + 1;
        breakCount.put(playerUUID, count);
        
        // Kiểm tra giới hạn số lượng block đào trong khoảng thời gian - giảm độ nghiêm ngặt
        if (count > MAX_BREAKS) {
            // Chỉ hủy mỗi lần thứ 2 khi vượt quá giới hạn để giảm cảm giác lag
            if (count % 2 == 0) {
                event.setCancelled(true);
                return;
            }
        }
        
        // Kiểm tra khối có phải là khối cần thu thập không - sử dụng cache
        String blockKey = block.getType().toString() + ";" + block.getData();
        boolean isMineable = CacheManager.isMineable(blockKey, k -> MineManager.checkBreak(block));
        
        if (!isMineable) {
            return;
        }
        
        // Lấy loại tài nguyên từ khối
        String resource = MineManager.getDrop(block);
        if (resource == null) {
            return;
        }
        
        // Kiểm tra xem plugin có đang bật tự động thu thập không
        if (!autoPickupEnabled) {
            return;
        }
        
        // Kiểm tra người chơi có bật tự động thu thập không
        Boolean isAutoPickup = CacheManager.getPlayerAttribute(player, "auto_pickup", null);
        if (isAutoPickup == null) {
            isAutoPickup = MineManager.isAutoPickup(player);
            CacheManager.setPlayerAttribute(player, "auto_pickup", isAutoPickup);
        }
        
        if (!isAutoPickup) {
            return;
        }
        
        // Kiểm tra quyền
        if (!player.hasPermission("storage.autopickup")) {
            return;
        }
        
        // Kiểm tra số lượng tài nguyên hiện tại và giới hạn - chỉ chạy một lần
        // trong luồng chính để tránh thay đổi dữ liệu không đồng bộ
        int currentAmount = MineManager.getPlayerBlock(player, resource);
        int maxStorage = MineManager.getMaxBlock(player);
        
        // Nếu đã đạt giới hạn, thông báo và thoát
        if (currentAmount >= maxStorage) {
            String playerKey = player.getName() + "_" + resource;
            Long lastNotification = storageFullNotificationCache.get(playerKey);
            
            // Chỉ thông báo nếu đã qua thời gian chờ
            if (lastNotification == null || now - lastNotification > NOTIFICATION_COOLDOWN) {
                // Cập nhật cache trước
                storageFullNotificationCache.put(playerKey, now);
                
                // Di chuyển thông báo vào luồng bất đồng bộ
                Bukkit.getScheduler().runTaskAsynchronously(Storage.getStorage(), () -> {
                    String materialName = CacheManager.getMaterialDisplayName(resource);
                    player.sendMessage(Chat.colorize(File.getMessage().getString("user.storage.full", "&c&l[Kho Khoáng Sản] &fKho của bạn đã đầy")));
                    player.sendMessage(Chat.colorize("&7Vật phẩm: &f" + materialName + " &7- Số lượng: &f" + currentAmount + "&7/&f" + maxStorage));
                });
            }
            return;
        }
        
        // Thu thập kinh nghiệm trước khi hủy drop - xử lý trong luồng chính
        int exp = event.getExpToDrop();
        event.setExpToDrop(0);
        player.giveExp(exp);
        
        // Hủy drop từ khối - xử lý trong luồng chính
        if (cancelDropEnabled) {
            event.setDropItems(false);
        }
        
        // Thu thập tài nguyên - xử lý trong luồng chính
        final boolean addSuccess = MineManager.addBlockAmount(player, resource, 1);
        
        // Nếu không thành công, không cần xử lý gì thêm
        if (!addSuccess) {
            return;
        }
        
        // Ghi nhận thống kê khai thác - đảm bảo được gọi ngay lập tức
        try {
            StatsManager.recordMining(player, 1);
        } catch (Exception e) {
            Storage.getStorage().getLogger().log(Level.WARNING, 
                "Lỗi khi ghi nhận thống kê khai thác cho " + player.getName() + ": " + e.getMessage(), e);
        }
        
        // Lưu lại giá trị để sử dụng trong lambda
        final String resourceFinal = resource;
        final int currentAmountFinal = currentAmount;
        
        // Các hoạt động không bắt buộc xử lý đồng bộ (thông báo, hiệu ứng, thống kê)
            Bukkit.getScheduler().runTaskAsynchronously(Storage.getStorage(), () -> {
            // Thông báo đã bị tắt để ngăn spam tin nhắn cho người chơi
            // Không cần ghi nhận thống kê ở đây nữa vì đã được gọi ở trên
            });
            
            // Hiệu ứng cần chạy trong luồng chính do sử dụng API Bukkit không an toàn với luồng phụ
        if (effectsEnabled) {
            try {
                // Kiểm tra thời gian giữa các hiệu ứng
                String playerName = player.getName();
                Long lastEffect = lastParticleEffectTime.get(playerName);
                
                // Chỉ hiển thị hiệu ứng nếu đã qua thời gian chờ
                if (lastEffect == null || now - lastEffect > PARTICLE_EFFECT_COOLDOWN) {
                    // Cập nhật thời gian hiệu ứng cuối
                    lastParticleEffectTime.put(playerName, now);
                    
                    // Gọi hiệu ứng với số lượng hạt được tối ưu
                    playCollectEffect(player, block.getLocation(), maxParticleCount);
                }
            } catch (Exception e) {
                Storage.getStorage().getLogger().warning("Lỗi khi tạo hiệu ứng thu thập: " + e.getMessage());
            }
        }
    }

    /**
     * Dọn dẹp cache định kỳ
     */
    public void scheduleCacheCleanup() {
        // Đặt lịch trình dọn dẹp cache mỗi 30 giây
        Bukkit.getScheduler().runTaskTimerAsynchronously(Storage.getStorage(), this::cleanupCache, 20 * 30, 20 * 30);
        
        // Đặt lịch trình reset bộ đếm đào block
        Bukkit.getScheduler().runTaskTimerAsynchronously(Storage.getStorage(), this::resetBreakCounters, 20 * RESET_INTERVAL, 20 * RESET_INTERVAL);
    }
    
    /**
     * Dọn dẹp cache
     */
    private void cleanupCache() {
        try {
            // Dọn dẹp thông báo kho đầy cũ
            long now = System.currentTimeMillis();
            List<String> keysToRemove = storageFullNotificationCache.entrySet().stream()
                    .filter(entry -> now - entry.getValue() > NOTIFICATION_COOLDOWN * 2)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            
            for (String key : keysToRemove) {
                storageFullNotificationCache.remove(key);
            }
            
            // Dọn dẹp thời gian hiệu ứng cuối
            List<String> effectKeysToRemove = lastParticleEffectTime.entrySet().stream()
                    .filter(entry -> now - entry.getValue() > PARTICLE_EFFECT_COOLDOWN * 5)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            
            for (String key : effectKeysToRemove) {
                lastParticleEffectTime.remove(key);
            }
            
            // Log thông tin ở chế độ debug
            if (Storage.getStorage().getLogger().isLoggable(Level.FINE)) {
                Storage.getStorage().getLogger().fine(
                    "BlockBreakEvent cache cleanup: Removed " + keysToRemove.size() + 
                    " storage notification entries and " + effectKeysToRemove.size() + " effect entries"
                );
            }
        } catch (Exception e) {
            // Bỏ qua lỗi khi dọn dẹp cache
            Storage.getStorage().getLogger().warning("Lỗi khi dọn dẹp cache: " + e.getMessage());
        }
    }
    
    /**
     * Đặt lại bộ đếm đào block cho tất cả người chơi
     */
    private void resetBreakCounters() {
        try {
            breakCount.clear();
            if (Storage.getStorage().getLogger().isLoggable(Level.FINE)) {
                Storage.getStorage().getLogger().fine("Đã đặt lại bộ đếm block cho tất cả người chơi");
            }
        } catch (Exception e) {
            // Bỏ qua lỗi khi đặt lại bộ đếm
        }
    }
    
    /**
     * Phát hiệu ứng thu thập tài nguyên
     * @param player Người chơi
     * @param location Vị trí khối
     * @param maxParticleCount Số lượng hạt tối đa
     */
    private void playCollectEffect(Player player, org.bukkit.Location location, int maxParticleCount) {
        // Giới hạn số lượng hạt xuống mức thấp hơn để tăng hiệu suất
        int particleLimit = Math.min(maxParticleCount, 10); // Giảm từ 15 xuống 10
        
        // Đọc cấu hình hiệu ứng từ config nếu có
        String effectConfig = File.getConfig().getString("effects.collect.particle", "VILLAGER_HAPPY:0.3:0.3:0.3:0.05:5"); // Giảm số lượng mặc định xuống 5
        String soundConfig = File.getConfig().getString("effects.collect.sound", "ENTITY_ITEM_PICKUP:0.2:0.8");
        
        // Xử lý hiệu ứng hạt
        if (effectConfig != null && !effectConfig.isEmpty()) {
            try {
                String[] parts = effectConfig.split(":");
                org.bukkit.Particle particleType;
                try {
                    particleType = org.bukkit.Particle.valueOf(parts[0]);
                } catch (IllegalArgumentException e) {
                    // Fallback to a safe particle if not found
                    particleType = org.bukkit.Particle.VILLAGER_HAPPY;
                }
                
                double offsetX = parts.length > 1 ? Double.parseDouble(parts[1]) : 0.3;
                double offsetY = parts.length > 2 ? Double.parseDouble(parts[2]) : 0.3;
                double offsetZ = parts.length > 3 ? Double.parseDouble(parts[3]) : 0.3;
                double speed = parts.length > 4 ? Double.parseDouble(parts[4]) : 0.05;
                int count = parts.length > 5 ? Integer.parseInt(parts[5]) : 5; // Giảm số lượng mặc định xuống 5
                
                // Giới hạn số lượng hạt để tránh ảnh hưởng hiệu suất - thêm kiểm tra số lượng tối đa
                count = Math.min(count, particleLimit);
                
                // Sử dụng vị trí khối + 0.5 để hiệu ứng xuất hiện ở giữa khối
                location = location.clone().add(0.5, 0.5, 0.5);
                
                // Chỉ hiển thị hiệu ứng cho người chơi đào - tối ưu bằng cách không hiển thị cho các người chơi khác
                player.spawnParticle(particleType, location, count, offsetX, offsetY, offsetZ, speed);
            } catch (Exception e) {
                // Bỏ qua nếu có lỗi khi tạo hiệu ứng
            }
        }
        
        // Xử lý âm thanh - không thay đổi
            if (soundConfig != null && !soundConfig.isEmpty()) {
            try {
                String[] parts = soundConfig.split(":");
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(parts[0]);
                float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 0.2f;
                float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 0.8f;
                
                player.playSound(location, sound, volume, pitch);
            } catch (Exception e) {
                // Bỏ qua nếu có lỗi khi phát âm thanh
            }
        }
    }
} 