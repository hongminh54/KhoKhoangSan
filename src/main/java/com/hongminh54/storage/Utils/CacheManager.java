package com.hongminh54.storage.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.hongminh54.storage.Manager.MineManager;
import com.hongminh54.storage.Storage;

/**
 * Lớp quản lý cache toàn hệ thống để cải thiện hiệu suất
 */
public class CacheManager {
    
    // Cache block data để tránh truy cập config
    private static final ConcurrentHashMap<String, String> materialNameCache = new ConcurrentHashMap<>();
    
    // Cache item stack
    private static final ConcurrentHashMap<String, ItemStack> itemStackCache = new ConcurrentHashMap<>();
    
    // Cache mineable blocks
    private static final ConcurrentHashMap<String, Boolean> mineableBlockCache = new ConcurrentHashMap<>();
    
    // Cache block value
    private static final ConcurrentHashMap<String, Integer> blockValueCache = new ConcurrentHashMap<>();
    
    // Cache người chơi
    private static final ConcurrentHashMap<UUID, Map<String, Object>> playerAttributeCache = new ConcurrentHashMap<>();
    
    // Cache lưu lượng truy cập
    private static final ConcurrentHashMap<String, Long> cacheHits = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> cacheMisses = new ConcurrentHashMap<>();
    
    // Các khóa theo dõi cho hiệu suất cache
    private static final String MATERIAL_CACHE = "material_name";
    private static final String ITEM_STACK_CACHE = "item_stack";
    private static final String MINEABLE_CACHE = "mineable_block";
    private static final String BLOCK_VALUE_CACHE = "block_value";
    private static final String PLAYER_ATTR_CACHE = "player_attribute";
    
    // Thời gian hết hạn cache mặc định (milliseconds)
    private static final long DEFAULT_CACHE_EXPIRY = 300000; // 5 phút
    
    // Cache thời gian hết hạn
    private static final ConcurrentHashMap<String, Long> cacheExpiry = new ConcurrentHashMap<>();
    
    // Cài đặt cache
    private static boolean enabled = true;
    private static int cleanupInterval = 30; // phút
    private static int maxItemStacks = 500;
    private static int maxMineableBlocks = 2000;
    private static int maxBlockValues = 200;
    private static int maxDisplayNames = 200;
    
    /**
     * Khởi tạo hệ thống cache
     */
    public static void initialize() {
        loadConfigSettings();
        
        if (!enabled) {
            Storage.getStorage().getLogger().info("Hệ thống cache đã bị tắt theo cấu hình.");
            return;
        }
        
        // Đăng ký task dọn dẹp cache định kỳ
        scheduleCacheCleanup();
        Storage.getStorage().getLogger().info("Hệ thống cache đã được khởi tạo với chu kỳ dọn dẹp " + cleanupInterval + " phút.");
    }
    
    /**
     * Đọc cài đặt cache từ config.yml
     */
    private static void loadConfigSettings() {
        FileConfiguration config = File.getConfig();
        enabled = config.getBoolean("cache.enabled", true);
        cleanupInterval = config.getInt("cache.cleanup_interval", 30);
        
        maxItemStacks = config.getInt("cache.max_items.item_stacks", 500);
        maxMineableBlocks = config.getInt("cache.max_items.mineable_blocks", 2000);
        maxBlockValues = config.getInt("cache.max_items.block_values", 200);
        maxDisplayNames = config.getInt("cache.max_items.display_names", 200);
    }
    
    /**
     * Kiểm tra xem block có thể đào được không
     * @param blockKey Khóa block (định dạng MATERIAL;DATA)
     * @param checkFunction Lambda kiểm tra nếu không có trong cache
     * @return true nếu có thể đào
     */
    public static boolean isMineable(String blockKey, java.util.function.Function<String, Boolean> checkFunction) {
        if (blockKey == null || !enabled) {
            return checkFunction.apply(blockKey);
        }
        
        return mineableBlockCache.computeIfAbsent(blockKey, checkFunction);
    }
    
    /**
     * Lấy giá trị của block
     * @param blockKey Khóa block
     * @param defaultValue Giá trị mặc định
     * @return Giá trị block
     */
    public static int getBlockValue(String blockKey, int defaultValue) {
        if (blockKey == null || !enabled) {
            return File.getConfig().getInt("blocks." + blockKey + ".value", defaultValue);
        }
        
        return blockValueCache.computeIfAbsent(blockKey, k -> {
            // Đọc từ config
            return File.getConfig().getInt("blocks." + k + ".value", defaultValue);
        });
    }
    
    /**
     * Lấy tên hiển thị của vật liệu
     * @param materialKey Khóa vật liệu
     * @return Tên hiển thị
     */
    public static String getMaterialDisplayName(String materialKey) {
        if (materialKey == null) {
            return "Unknown";
        }
        
        // Nếu cache bị tắt, truy cập config trực tiếp
        if (!enabled) {
            String display = File.getConfig().getString("items." + materialKey);
            if (display != null) {
                return display;
            }
            
            // Nếu không có tên tùy chỉnh, trả về tên mặc định
            String[] parts = materialKey.split(";");
            String material = parts[0];
            return formatMaterialName(material);
        }
        
        // Kiểm tra cache trước
        if (materialNameCache.containsKey(materialKey)) {
            return materialNameCache.get(materialKey);
        }
        
        // Không có trong cache, truy cập config
        String display = File.getConfig().getString("items." + materialKey);
        if (display != null) {
            materialNameCache.put(materialKey, display);
            return display;
        }
        
        // Nếu không có tên tùy chỉnh, trả về tên mặc định
        String[] parts = materialKey.split(";");
        String material = parts[0];
        
        // Chuyển đổi tên vật liệu
        String friendlyName = formatMaterialName(material);
        materialNameCache.put(materialKey, friendlyName);
        return friendlyName;
    }
    
    /**
     * Định dạng tên vật liệu thành dạng thân thiện với người dùng
     */
    private static String formatMaterialName(String material) {
        // Chuyển đổi tên vật liệu thành tên hiển thị thân thiện
        String friendlyName = material.replace("_", " ").toLowerCase();
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        
        for (char c : friendlyName.toCharArray()) {
            if (c == ' ') {
                result.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }
    
    /**
     * Lấy ItemStack từ cache
     * @param material Vật liệu
     * @param displayName Tên hiển thị
     * @param lore Danh sách mô tả
     * @return ItemStack
     */
    public static ItemStack getCachedItemStack(Material material, String displayName, List<String> lore) {
        if (!enabled) {
            // Nếu cache bị tắt, tạo item mới mỗi lần
            return com.hongminh54.storage.Manager.ItemManager.createItem(material, displayName, lore);
        }
        
        String cacheKey = material.toString() + ":" + displayName + ":" + (lore != null ? String.join("|", lore) : "");
        
        return itemStackCache.computeIfAbsent(cacheKey, k -> {
            // Tạo ItemStack mới nếu không tìm thấy trong cache
            return com.hongminh54.storage.Manager.ItemManager.createItem(material, displayName, lore);
        });
    }
    
    /**
     * Lưu thuộc tính người chơi vào cache
     * @param player Người chơi
     * @param key Khóa thuộc tính
     * @param value Giá trị thuộc tính
     */
    public static void setPlayerAttribute(Player player, String key, Object value) {
        if (player == null || key == null || !enabled) {
            return;
        }
        
        UUID playerUUID = player.getUniqueId();
        Map<String, Object> attributes = playerAttributeCache.computeIfAbsent(playerUUID, k -> new HashMap<>());
        attributes.put(key, value);
    }
    
    /**
     * Lấy thuộc tính người chơi từ cache
     * @param player Người chơi
     * @param key Khóa thuộc tính
     * @param defaultValue Giá trị mặc định
     * @return Giá trị thuộc tính
     */
    @SuppressWarnings("unchecked")
    public static <T> T getPlayerAttribute(Player player, String key, T defaultValue) {
        if (player == null || key == null || !enabled) {
            return defaultValue;
        }
        
        UUID playerUUID = player.getUniqueId();
        Map<String, Object> attributes = playerAttributeCache.get(playerUUID);
        
        if (attributes == null || !attributes.containsKey(key)) {
            return defaultValue;
        }
        
        try {
            return (T) attributes.get(key);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }
    
    /**
     * Xóa cache của người chơi khi đăng xuất
     * @param player Người chơi
     */
    public static void removePlayerCache(Player player) {
        if (player == null || !enabled) {
            return;
        }
        
        playerAttributeCache.remove(player.getUniqueId());
    }
    
    /**
     * Dọn dẹp cache định kỳ
     */
    private static void scheduleCacheCleanup() {
        // Chạy theo thời gian cấu hình
        long intervalTicks = 20 * 60 * cleanupInterval;
        
        Bukkit.getScheduler().runTaskTimerAsynchronously(Storage.getStorage(), () -> {
            if (!enabled) return;
            
            long start = System.currentTimeMillis();
            int totalRemoved = 0;
            
            // 1. Dọn dẹp materialNameCache nếu quá lớn
            if (materialNameCache.size() > maxDisplayNames) {
                int targetSize = maxDisplayNames / 2;
                List<String> keysToRemove = materialNameCache.keySet().stream()
                    .limit(materialNameCache.size() - targetSize)
                    .collect(Collectors.toList());
                
                for (String key : keysToRemove) {
                    materialNameCache.remove(key);
                }
                
                totalRemoved += keysToRemove.size();
            }
            
            // 2. Dọn dẹp itemStackCache nếu quá lớn
            if (itemStackCache.size() > maxItemStacks) {
                int targetSize = maxItemStacks / 2;
                List<String> keysToRemove = itemStackCache.keySet().stream()
                    .limit(itemStackCache.size() - targetSize)
                    .collect(Collectors.toList());
                
                for (String key : keysToRemove) {
                    itemStackCache.remove(key);
                }
                
                totalRemoved += keysToRemove.size();
            }
            
            // 3. Dọn dẹp mineableBlockCache nếu quá lớn
            if (mineableBlockCache.size() > maxMineableBlocks) {
                int targetSize = maxMineableBlocks / 2;
                List<String> keysToRemove = mineableBlockCache.keySet().stream()
                    .limit(mineableBlockCache.size() - targetSize)
                    .collect(Collectors.toList());
                
                for (String key : keysToRemove) {
                    mineableBlockCache.remove(key);
                }
                
                totalRemoved += keysToRemove.size();
            }
            
            // 4. Dọn dẹp blockValueCache nếu quá lớn
            if (blockValueCache.size() > maxBlockValues) {
                int targetSize = maxBlockValues / 2;
                List<String> keysToRemove = blockValueCache.keySet().stream()
                    .limit(blockValueCache.size() - targetSize)
                    .collect(Collectors.toList());
                
                for (String key : keysToRemove) {
                    blockValueCache.remove(key);
                }
                
                totalRemoved += keysToRemove.size();
            }
            
            // 5. Dọn dẹp playerAttributeCache cho người chơi không online
            List<UUID> offlinePlayers = playerAttributeCache.keySet().stream()
                .filter(uuid -> Bukkit.getPlayer(uuid) == null || !Bukkit.getPlayer(uuid).isOnline())
                .collect(Collectors.toList());
                
            for (UUID uuid : offlinePlayers) {
                playerAttributeCache.remove(uuid);
            }
            
            totalRemoved += offlinePlayers.size();
            
            // Log kết quả nếu ở chế độ debug
            Storage.getStorage().getLogger().info(
                "Cache Cleanup: Đã xóa " + totalRemoved + " mục cache trong " + (System.currentTimeMillis() - start) + "ms. " +
                "Kích thước hiện tại: " + 
                "materialName=" + materialNameCache.size() + ", " +
                "itemStack=" + itemStackCache.size() + ", " +
                "mineableBlock=" + mineableBlockCache.size() + ", " +
                "blockValue=" + blockValueCache.size() + ", " +
                "playerAttribute=" + playerAttributeCache.size()
            );
        }, intervalTicks, intervalTicks);
    }
    
    /**
     * Khởi tạo lại cache khi reload plugin
     */
    public static void reload() {
        // Đọc lại cài đặt
        loadConfigSettings();
        
        // Clear caches
        materialNameCache.clear();
        itemStackCache.clear();
        mineableBlockCache.clear();
        blockValueCache.clear();
        
        // Giữ lại cache người chơi
        Storage.getStorage().getLogger().info("Đã làm mới hệ thống cache.");
    }
    
    /**
     * Ghi lại cache hit
     * @param cacheType Loại cache
     */
    private static void recordCacheHit(String cacheType) {
        if (!enabled || !File.getConfig().getBoolean("cache.track_performance", false)) {
            return;
        }
        
        cacheHits.compute(cacheType, (k, v) -> v == null ? 1 : v + 1);
    }
    
    /**
     * Ghi lại cache miss
     * @param cacheType Loại cache
     */
    private static void recordCacheMiss(String cacheType) {
        if (!enabled || !File.getConfig().getBoolean("cache.track_performance", false)) {
            return;
        }
        
        cacheMisses.compute(cacheType, (k, v) -> v == null ? 1 : v + 1);
    }
    
    /**
     * Kiểm tra xem cache có hết hạn chưa
     * @param key Khóa cache
     * @param cacheType Loại cache
     * @return true nếu cache đã hết hạn
     */
    private static boolean isCacheExpired(String key, String cacheType) {
        if (!enabled) {
            return true;
        }
        
        String expiryKey = cacheType + ":" + key;
        Long expiry = cacheExpiry.get(expiryKey);
        return expiry == null || System.currentTimeMillis() > expiry;
    }
    
    /**
     * Cập nhật thời gian hết hạn cho cache
     * @param key Khóa cache
     * @param cacheType Loại cache
     * @param duration Thời gian (ms) cho tới khi hết hạn
     */
    private static void updateCacheExpiry(String key, String cacheType, long duration) {
        if (!enabled) {
            return;
        }
        
        String expiryKey = cacheType + ":" + key;
        cacheExpiry.put(expiryKey, System.currentTimeMillis() + duration);
    }
    
    /**
     * Lấy tỷ lệ hit của cache
     * @return Map chứa tỷ lệ hit của từng loại cache
     */
    public static Map<String, Double> getCacheHitRatio() {
        Map<String, Double> ratios = new HashMap<>();
        
        for (String cacheType : new String[]{MATERIAL_CACHE, ITEM_STACK_CACHE, MINEABLE_CACHE, BLOCK_VALUE_CACHE, PLAYER_ATTR_CACHE}) {
            long hits = cacheHits.getOrDefault(cacheType, 0L);
            long misses = cacheMisses.getOrDefault(cacheType, 0L);
            long total = hits + misses;
            
            double ratio = total > 0 ? (double) hits / total : 0.0;
            ratios.put(cacheType, ratio);
        }
        
        return ratios;
    }
    
    /**
     * Đồng bộ hóa dữ liệu cache với Database
     * @param playerName Tên người chơi
     */
    public static void synchronizeCache(String playerName) {
        if (!enabled || playerName == null) {
            return;
        }
        
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            return;
        }
        
        // Đồng bộ thống kê người chơi
        StatsManager.loadPlayerStats(player, true);
        
        // Đồng bộ dữ liệu kho
        try {
            MineManager.loadPlayerData(player);
        } catch (Exception e) {
            Storage.getStorage().getLogger().warning("Không thể đồng bộ dữ liệu kho cho " + playerName + ": " + e.getMessage());
        }
        
        // Đánh dấu đã đồng bộ
        setPlayerAttribute(player, "last_sync_time", System.currentTimeMillis());
        
        if (File.getConfig().getBoolean("cache.debug_sync", false)) {
            Storage.getStorage().getLogger().info("Đã đồng bộ cache cho người chơi " + playerName);
        }
    }
    
    /**
     * Đồng bộ hóa tất cả cache người chơi với Database
     */
    public static void synchronizeAllPlayerCache() {
        if (!enabled) {
            return;
        }
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            synchronizeCache(player.getName());
        }
        
        // Cập nhật các bảng xếp hạng
        LeaderboardManager.updateAllLeaderboardsAsync();
        
        if (File.getConfig().getBoolean("cache.debug_sync", false)) {
            Storage.getStorage().getLogger().info("Đã đồng bộ cache cho tất cả người chơi và bảng xếp hạng");
        }
    }
    
    /**
     * Kiểm tra và đồng bộ hóa cache nếu cần
     * @param player Người chơi
     * @return true nếu đã đồng bộ hóa
     */
    public static boolean checkAndSynchronizeCache(Player player) {
        if (!enabled || player == null) {
            return false;
        }
        
        Long lastSyncTime = getPlayerAttribute(player, "last_sync_time", 0L);
        long currentTime = System.currentTimeMillis();
        long syncInterval = File.getConfig().getLong("cache.auto_sync_interval", 300000); // 5 phút mặc định
        
        if (currentTime - lastSyncTime > syncInterval) {
            synchronizeCache(player.getName());
            return true;
        }
        
        return false;
    }
    
    /**
     * Lấy thống kê hiệu suất cache
     * @return Thông tin hiệu suất dưới dạng danh sách chuỗi
     */
    public static List<String> getCacheStats() {
        List<String> stats = new ArrayList<>();
        
        stats.add("&e=== Thống kê Cache ===");
        
        // Thông tin kích thước cache
        stats.add("&7Kích thước Cache:");
        stats.add("&7- Tên vật liệu: &f" + materialNameCache.size() + "/" + maxDisplayNames);
        stats.add("&7- ItemStack: &f" + itemStackCache.size() + "/" + maxItemStacks);
        stats.add("&7- Block có thể đào: &f" + mineableBlockCache.size() + "/" + maxMineableBlocks);
        stats.add("&7- Giá trị block: &f" + blockValueCache.size() + "/" + maxBlockValues);
        stats.add("&7- Thuộc tính người chơi: &f" + playerAttributeCache.size() + "/" + Bukkit.getOnlinePlayers().size());
        
        // Tỷ lệ cache hit
        Map<String, Double> hitRatios = getCacheHitRatio();
        stats.add("&7Tỷ lệ Cache Hit:");
        for (Map.Entry<String, Double> entry : hitRatios.entrySet()) {
            String cacheType = entry.getKey();
            double ratio = entry.getValue() * 100;
            stats.add(String.format("&7- %s: &f%.1f%%", formatCacheTypeName(cacheType), ratio));
        }
        
        // Thông tin bộ nhớ hệ thống
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        stats.add(String.format("&7Bộ nhớ sử dụng: &f%dMB/%dMB", usedMemory, totalMemory));
        
        return stats;
    }
    
    /**
     * Định dạng tên loại cache
     */
    private static String formatCacheTypeName(String cacheType) {
        switch (cacheType) {
            case MATERIAL_CACHE: return "Tên vật liệu";
            case ITEM_STACK_CACHE: return "ItemStack";
            case MINEABLE_CACHE: return "Block có thể đào";
            case BLOCK_VALUE_CACHE: return "Giá trị block";
            case PLAYER_ATTR_CACHE: return "Thuộc tính người chơi";
            default: return cacheType;
        }
    }
} 