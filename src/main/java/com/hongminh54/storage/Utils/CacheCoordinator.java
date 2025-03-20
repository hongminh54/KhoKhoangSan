package com.hongminh54.storage.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.hongminh54.storage.Manager.MineManager;
import com.hongminh54.storage.Storage;

/**
 * Lớp điều phối cache - quản lý đồng bộ hóa và theo dõi hiệu suất cache
 */
public class CacheCoordinator {
    
    // Theo dõi lượt truy cập cache
    private static final ConcurrentHashMap<String, Long> cacheHits = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> cacheMisses = new ConcurrentHashMap<>();
    
    // Khóa theo dõi hiệu suất
    private static final String MATERIAL_CACHE = "material_name";
    private static final String ITEM_STACK_CACHE = "item_stack";
    private static final String MINEABLE_CACHE = "mineable_block";
    private static final String BLOCK_VALUE_CACHE = "block_value";
    private static final String PLAYER_ATTR_CACHE = "player_attribute";
    private static final String STATS_CACHE = "player_stats";
    private static final String LEADERBOARD_CACHE = "leaderboard";
    
    // Thời gian đồng bộ hóa gần nhất cho mỗi người chơi
    private static final ConcurrentHashMap<String, Long> lastSyncTime = new ConcurrentHashMap<>();
    
    // Khóa đồng bộ hóa để tránh xung đột
    private static final Object playerSyncLock = new Object();
    private static final Object leaderboardSyncLock = new Object();
    
    /**
     * Ghi lại hit cache
     * @param cacheType Loại cache
     */
    public static void recordCacheHit(String cacheType) {
        if (!File.getConfig().getBoolean("cache.track_performance", false)) {
            return;
        }
        
        cacheHits.compute(cacheType, (k, v) -> v == null ? 1 : v + 1);
    }
    
    /**
     * Ghi lại miss cache
     * @param cacheType Loại cache
     */
    public static void recordCacheMiss(String cacheType) {
        if (!File.getConfig().getBoolean("cache.track_performance", false)) {
            return;
        }
        
        cacheMisses.compute(cacheType, (k, v) -> v == null ? 1 : v + 1);
    }
    
    /**
     * Lấy tỷ lệ hit của cache
     * @return Map chứa tỷ lệ hit của từng loại cache
     */
    public static Map<String, Double> getCacheHitRatio() {
        Map<String, Double> ratios = new HashMap<>();
        
        for (String cacheType : new String[]{
                MATERIAL_CACHE, ITEM_STACK_CACHE, MINEABLE_CACHE,
                BLOCK_VALUE_CACHE, PLAYER_ATTR_CACHE, STATS_CACHE, LEADERBOARD_CACHE
        }) {
            long hits = cacheHits.getOrDefault(cacheType, 0L);
            long misses = cacheMisses.getOrDefault(cacheType, 0L);
            long total = hits + misses;
            
            double ratio = total > 0 ? (double) hits / total : 0.0;
            ratios.put(cacheType, ratio);
        }
        
        return ratios;
    }
    
    /**
     * Đồng bộ hóa dữ liệu cache với Database cho một người chơi
     * @param playerName Tên người chơi
     */
    public static void synchronizePlayerCache(String playerName) {
        if (playerName == null) {
            return;
        }
        
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            return;
        }
        
        // Sử dụng khóa để đảm bảo chỉ một luồng đồng bộ hóa cho mỗi người chơi
        synchronized (playerSyncLock) {
            // Đồng bộ thống kê người chơi
            StatsManager.loadPlayerStats(player, true);
            
            // Đồng bộ dữ liệu kho
            try {
                MineManager.loadPlayerData(player);
            } catch (Exception e) {
                Storage.getStorage().getLogger().warning("Không thể đồng bộ dữ liệu kho cho " + playerName + ": " + e.getMessage());
            }
            
            // Cập nhật thời gian đồng bộ
            lastSyncTime.put(playerName, System.currentTimeMillis());
            
            if (File.getConfig().getBoolean("cache.debug_sync", false)) {
                Storage.getStorage().getLogger().info("Đã đồng bộ cache cho người chơi " + playerName);
            }
        }
    }
    
    /**
     * Đồng bộ hóa tất cả cache người chơi với Database
     */
    public static void synchronizeAllPlayerCache() {
        // Đồng bộ từng người chơi đang online
        for (Player player : Bukkit.getOnlinePlayers()) {
            synchronizePlayerCache(player.getName());
        }
        
        // Cập nhật các bảng xếp hạng
        synchronizeLeaderboards();
        
        if (File.getConfig().getBoolean("cache.debug_sync", false)) {
            Storage.getStorage().getLogger().info("Đã đồng bộ cache cho tất cả người chơi và bảng xếp hạng");
        }
    }
    
    /**
     * Đồng bộ hóa bảng xếp hạng
     */
    public static void synchronizeLeaderboards() {
        synchronized (leaderboardSyncLock) {
            LeaderboardManager.updateAllLeaderboardsAsync();
        }
    }
    
    /**
     * Kiểm tra và đồng bộ hóa cache nếu cần
     * @param player Người chơi
     * @return true nếu đã đồng bộ hóa
     */
    public static boolean checkAndSynchronizeCache(Player player) {
        if (player == null) {
            return false;
        }
        
        Long playerLastSync = lastSyncTime.get(player.getName());
        long currentTime = System.currentTimeMillis();
        long syncInterval = File.getConfig().getLong("cache.auto_sync_interval", 300000); // 5 phút mặc định
        
        if (playerLastSync == null || currentTime - playerLastSync > syncInterval) {
            synchronizePlayerCache(player.getName());
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
        
        // Thời gian đồng bộ
        stats.add("&7Thời gian đồng bộ cache (phút):");
        stats.add("&7- Người chơi: &f" + (File.getConfig().getLong("cache.auto_sync_interval", 300000) / 60000));
        stats.add("&7- Bảng xếp hạng: &f" + File.getConfig().getInt("settings.leaderboard_update_delay", 30));
        
        // Tỷ lệ cache hit
        Map<String, Double> hitRatios = getCacheHitRatio();
        stats.add("&7Tỷ lệ Cache Hit:");
        for (Map.Entry<String, Double> entry : hitRatios.entrySet()) {
            String cacheType = entry.getKey();
            double ratio = entry.getValue() * 100;
            stats.add(String.format("&7- %s: &f%.1f%%", formatCacheTypeName(cacheType), ratio));
        }
        
        // Thông tin người chơi được đồng bộ
        int syncedPlayers = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (lastSyncTime.containsKey(player.getName())) {
                syncedPlayers++;
            }
        }
        stats.add("&7Người chơi đã đồng bộ: &f" + syncedPlayers + "/" + Bukkit.getOnlinePlayers().size());
        
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
            case STATS_CACHE: return "Thống kê người chơi";
            case LEADERBOARD_CACHE: return "Bảng xếp hạng";
            default: return cacheType;
        }
    }
    
    /**
     * Xóa cache của người chơi khi họ đăng xuất
     * @param playerName Tên người chơi
     */
    public static void clearPlayerCache(String playerName) {
        lastSyncTime.remove(playerName);
    }
    
    /**
     * Lên lịch đồng bộ hóa tự động
     */
    public static void scheduleAutomaticSynchronization() {
        long interval = File.getConfig().getLong("cache.global_sync_interval", 1800000); // 30 phút mặc định
        
        Bukkit.getScheduler().runTaskTimerAsynchronously(Storage.getStorage(), () -> {
            synchronizeAllPlayerCache();
        }, interval / 50, interval / 50); // Chuyển đổi từ ms sang ticks (20 ticks = 1 giây)
        
        Storage.getStorage().getLogger().info("Đã lập lịch đồng bộ hóa cache tự động với chu kỳ " + (interval / 60000) + " phút");
    }
    
    /**
     * Khởi tạo hệ thống đồng bộ hóa cache
     */
    public static void initialize() {
        // Lên lịch đồng bộ hóa tự động
        scheduleAutomaticSynchronization();
        
        // Khởi tạo các giá trị theo dõi
        for (String cacheType : new String[]{
                MATERIAL_CACHE, ITEM_STACK_CACHE, MINEABLE_CACHE,
                BLOCK_VALUE_CACHE, PLAYER_ATTR_CACHE, STATS_CACHE, LEADERBOARD_CACHE
        }) {
            cacheHits.put(cacheType, 0L);
            cacheMisses.put(cacheType, 0L);
        }
        
        Storage.getStorage().getLogger().info("Hệ thống đồng bộ hóa cache đã được khởi tạo");
    }
} 