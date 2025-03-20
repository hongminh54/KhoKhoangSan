package com.hongminh54.storage.Utils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.hongminh54.storage.Manager.MineManager;
import com.hongminh54.storage.Storage;
import com.tchristofferson.configupdater.ConfigUpdater;

import net.xconfig.bukkit.model.SimpleConfigurationManager;

public class File {

    private static FileConfiguration message;
    private static FileConfiguration config;
    private static FileConfiguration events;
    
    // Cache các cấu hình để tăng hiệu suất
    private static FileConfiguration configCache = null;
    private static FileConfiguration messageCache = null;
    private static FileConfiguration guiStorageCache = null;
    private static FileConfiguration itemStorageCache = null;
    private static long lastConfigRefresh = 0;
    private static final long CONFIG_CACHE_DURATION = 30000; // 30 giây

    public static SimpleConfigurationManager getFileSetting() {
        return SimpleConfigurationManager.get();
    }

    public static FileConfiguration getConfig() {
        long currentTime = System.currentTimeMillis();
        
        // Trả về cache nếu còn hiệu lực
        if (configCache != null && currentTime - lastConfigRefresh < CONFIG_CACHE_DURATION) {
            return configCache;
        }
        
        // Lấy cấu hình mới và cập nhật cache
        configCache = getFileSetting().get("config.yml");
        lastConfigRefresh = currentTime;
        return configCache;
    }

    public static FileConfiguration getMessage() {
        long currentTime = System.currentTimeMillis();
        
        // Trả về cache nếu còn hiệu lực
        if (messageCache != null && currentTime - lastConfigRefresh < CONFIG_CACHE_DURATION) {
            return messageCache;
        }
        
        // Lấy cấu hình mới và cập nhật cache
        messageCache = getFileSetting().get("message.yml");
        lastConfigRefresh = currentTime;
        return messageCache;
    }

    public static FileConfiguration getGUIStorage() {
        long currentTime = System.currentTimeMillis();
        
        // Trả về cache nếu còn hiệu lực
        if (guiStorageCache != null && currentTime - lastConfigRefresh < CONFIG_CACHE_DURATION) {
            return guiStorageCache;
        }
        
        // Lấy cấu hình mới và cập nhật cache
        guiStorageCache = getFileSetting().get("GUI/storage.yml");
        lastConfigRefresh = currentTime;
        return guiStorageCache;
    }

    public static FileConfiguration getItemStorage() {
        long currentTime = System.currentTimeMillis();
        
        // Trả về cache nếu còn hiệu lực
        if (itemStorageCache != null && currentTime - lastConfigRefresh < CONFIG_CACHE_DURATION) {
            return itemStorageCache;
        }
        
        // Lấy cấu hình mới và cập nhật cache
        itemStorageCache = getFileSetting().get("GUI/items.yml");
        lastConfigRefresh = currentTime;
        return itemStorageCache;
    }

    public static FileConfiguration getGUIConfig(String name) {
        return getFileSetting().get("GUI/" + name + ".yml");
    }

    public static void loadFiles() {
        getFileSetting().build("", false, "config.yml", "message.yml");
        loadEvents();
        loadDefaultConfig();
        
        // Khởi tạo cache
        configCache = getFileSetting().get("config.yml");
        messageCache = getFileSetting().get("message.yml");
        lastConfigRefresh = System.currentTimeMillis();
    }

    public static void reloadFiles() {
        getFileSetting().reload("config.yml", "message.yml", "GUI/storage.yml", "GUI/items.yml", "GUI/stats.yml", "GUI/leaderboard.yml");
        for (Player p : Bukkit.getOnlinePlayers()) {
            MineManager.savePlayerData(p);
            MineManager.loadPlayerData(p);
        }
        
        // Cập nhật cache
        configCache = getFileSetting().get("config.yml");
        messageCache = getFileSetting().get("message.yml");
        guiStorageCache = getFileSetting().get("GUI/storage.yml");
        itemStorageCache = getFileSetting().get("GUI/items.yml");
        lastConfigRefresh = System.currentTimeMillis();
    }

    public static void loadGUI() {
        getFileSetting().build("", false, "GUI/storage.yml", "GUI/items.yml", "GUI/stats.yml", "GUI/leaderboard.yml");
        
        // Kiểm tra xem các file đã được tải thành công chưa
        checkGUIFilesExist();
    }
    
    /**
     * Kiểm tra xem các file GUI cần thiết đã tồn tại chưa
     */
    private static void checkGUIFilesExist() {
        String[] guiFiles = {"storage.yml", "items.yml", "stats.yml", "leaderboard.yml"};
        
        for (String file : guiFiles) {
            FileConfiguration config = getFileSetting().get("GUI/" + file);
            if (config == null) {
                Storage.getStorage().getLogger().warning("Cannot find GUI file: GUI/" + file);
                // Thử tải lại file
                getFileSetting().build("", false, "GUI/" + file);
                
                // Kiểm tra lại sau khi thử tải
                if (getFileSetting().get("GUI/" + file) == null) {
                    Storage.getStorage().getLogger().severe("Failed to load GUI file: GUI/" + file + ". This may cause errors!");
                } else {
                    Storage.getStorage().getLogger().info("Successfully loaded GUI file: GUI/" + file);
                }
            }
        }
    }

    public static void saveFiles() {
        getFileSetting().save("config.yml", "message.yml", "GUI/storage.yml", "GUI/items.yml", "GUI/stats.yml", "GUI/leaderboard.yml");
        saveEvents();
    }

    public static void updateConfig() {
        getFileSetting().save("config.yml");
        java.io.File configFile = new java.io.File(Storage.getStorage().getDataFolder(), "config.yml");
        FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(Storage.getStorage().getResource("config.yml")), StandardCharsets.UTF_8));
        FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
        int default_configVersion = defaultConfig.getInt("config_version");
        int current_configVersion = defaultConfig.contains("config_version") ? defaultConfig.getInt("config_version") : 0;
        if (default_configVersion > current_configVersion || default_configVersion < current_configVersion) {
            List<String> default_whitelist_fortune = defaultConfig.getStringList("whitelist_fortune");
            List<String> current_whitelist_fortune = currentConfig.getStringList("whitelist_fortune");
            List<String> default_blacklist_world = defaultConfig.getStringList("blacklist_world");
            List<String> current_blacklist_world = currentConfig.getStringList("blacklist_world");
            Storage.getStorage().getLogger().log(Level.WARNING, "Your config is updating...");
            if (current_whitelist_fortune.isEmpty()) {
                getConfig().set("whitelist_fortune", default_whitelist_fortune);
                getFileSetting().save("config.yml");
            }
            if (current_blacklist_world.isEmpty()) {
                getConfig().set("blacklist_world", default_blacklist_world);
                getFileSetting().save("config.yml");
            }
            try {
                ConfigUpdater.update(Storage.getStorage(), "config.yml", configFile, "items", "blocks", "worth");
                Storage.getStorage().getLogger().log(Level.WARNING, "Your config have been updated successful");
            } catch (IOException e) {
                Storage.getStorage().getLogger().log(Level.WARNING, "Can not update config by it self, please backup and rename your config then restart to get newest config!!");
                e.printStackTrace();
            }
            getFileSetting().reload("config.yml");
        }
    }

    public static void updateMessage() {
        getFileSetting().save("message.yml");
        java.io.File configFile = new java.io.File(Storage.getStorage().getDataFolder(), "message.yml");
        FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(Storage.getStorage().getResource("message.yml")), StandardCharsets.UTF_8));
        FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
        int default_configVersion = defaultConfig.getInt("message_version");
        int current_configVersion = defaultConfig.contains("message_version") ? defaultConfig.getInt("message_version") : 0;
        if (default_configVersion > current_configVersion || default_configVersion < current_configVersion) {
            Storage.getStorage().getLogger().log(Level.WARNING, "Your message is updating...");
            List<String> default_admin_help = defaultConfig.getStringList("admin.help");
            List<String> default_user_help = defaultConfig.getStringList("user.help");
            List<String> current_admin_help = currentConfig.getStringList("admin.help");
            List<String> current_user_help = currentConfig.getStringList("user.help");
            if (default_admin_help.size() != current_admin_help.size()) {
                getConfig().set("admin.help", default_admin_help);
                getFileSetting().save("message.yml");
            }
            if (default_user_help.size() != current_user_help.size()) {
                getConfig().set("user.help", default_user_help);
                getFileSetting().save("message.yml");
            }
            try {
                ConfigUpdater.update(Storage.getStorage(), "message.yml", configFile);
                Storage.getStorage().getLogger().log(Level.WARNING, "Your message have been updated successful");
            } catch (IOException e) {
                Storage.getStorage().getLogger().log(Level.WARNING, "Can not update message by it self, please backup and rename your message then restart to get newest message!!");
                e.printStackTrace();
            }
            getFileSetting().reload("message.yml");
        }
    }

    /**
     * Lấy file cấu hình sự kiện
     * @return FileConfiguration của events.yml
     */
    public static FileConfiguration getEvents() {
        if (events == null) {
            loadEvents();
        }
        return events;
    }
    
    /**
     * Tải file events.yml
     */
    public static void loadEvents() {
        try {
            // Tạo file events.yml nếu chưa tồn tại
            java.io.File eventsFile = new java.io.File(Storage.getStorage().getDataFolder(), "events.yml");
            if (!eventsFile.exists()) {
                Storage.getStorage().saveResource("events.yml", false);
            }
            events = YamlConfiguration.loadConfiguration(eventsFile);
        } catch (Exception e) {
            e.printStackTrace();
            // Nếu có lỗi, tạo một cấu hình trống
            events = new YamlConfiguration();
        }
    }
    
    /**
     * Lưu file events.yml
     */
    public static void saveEvents() {
        try {
            if (events != null) {
                java.io.File eventsFile = new java.io.File(Storage.getStorage().getDataFolder(), "events.yml");
                events.save(eventsFile);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Storage.getStorage().getLogger().severe("Cannot save events.yml file!");
        }
    }

    /**
     * Tải cấu hình mặc định từ plugin JAR
     */
    private static void loadDefaultConfig() {
        Storage plugin = Storage.getStorage();
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
        
        // Thêm các cấu hình mặc định nếu chưa có
        if (!config.contains("settings.transfer_percentage")) {
            config.set("settings.transfer_percentage", 25);
            plugin.getLogger().info("Đã thêm cấu hình mặc định cho tỷ lệ chuyển tài nguyên: 25%");
        }
        
        if (!config.contains("settings.max_particle_count")) {
            config.set("settings.max_particle_count", 15);
            plugin.getLogger().info("Đã thêm cấu hình mặc định cho số hạt hiệu ứng tối đa: 15");
        }
        
        if (!config.contains("settings.large_transfer_threshold")) {
            config.set("settings.large_transfer_threshold", 100);
            plugin.getLogger().info("Đã thêm cấu hình mặc định cho ngưỡng giao dịch lớn: 100");
        }
        
        if (!config.contains("settings.max_history_display")) {
            config.set("settings.max_history_display", 50);
            plugin.getLogger().info("Đã thêm cấu hình mặc định cho số lượng lịch sử hiển thị tối đa: 50");
        }
        
        if (!config.contains("settings.search_timeout")) {
            config.set("settings.search_timeout", 30);
            plugin.getLogger().info("Đã thêm cấu hình mặc định cho thời gian chờ tìm kiếm: 30 giây");
        }
        
        if (!config.contains("settings.max_player_name_length")) {
            config.set("settings.max_player_name_length", 16);
            plugin.getLogger().info("Đã thêm cấu hình mặc định cho độ dài tên người chơi tối đa: 16");
        }
        
        // Thêm cấu hình mặc định cho hiệu ứng nếu chưa có
        addDefaultSoundEffect("effects.transfer_success.sender_sound", "ENTITY_PLAYER_LEVELUP:0.5:1.2");
        addDefaultSoundEffect("effects.transfer_success.receiver_sound", "ENTITY_EXPERIENCE_ORB_PICKUP:0.5:1.0");
        addDefaultSoundEffect("effects.transfer_fail.sound", "ENTITY_VILLAGER_NO:1.0:1.0");
        addDefaultSoundEffect("effects.permission_fail.sound", "ENTITY_VILLAGER_NO:1.0:1.0");
        addDefaultSoundEffect("effects.search_open.sound", "BLOCK_NOTE_BLOCK_PLING:0.5:1.2");
        addDefaultSoundEffect("effects.search_success.sound", "ENTITY_EXPERIENCE_ORB_PICKUP:0.5:1.0");
        addDefaultSoundEffect("effects.search_fail.sound", "ENTITY_VILLAGER_NO:0.5:1.0");
        addDefaultSoundEffect("effects.search_cancel.sound", "ENTITY_VILLAGER_NO:0.5:1.0");
        addDefaultSoundEffect("effects.storage_open.sound", "BLOCK_CHEST_OPEN:0.5:1.0");
        addDefaultSoundEffect("effects.gui_click.sound", "UI_BUTTON_CLICK:0.5:1.0");
        addDefaultSoundEffect("effects.history_view.sound", "BLOCK_NOTE_BLOCK_PLING:0.5:1.2");
        
        // Thêm cấu hình mặc định cho hiệu ứng hạt nếu chưa có
        addDefaultParticleEffect("effects.transfer_success.sender_particle", "VILLAGER_HAPPY:0.5:0.5:0.5:0.1:10");
        addDefaultParticleEffect("effects.transfer_success.receiver_particle", "VILLAGER_HAPPY:0.5:0.5:0.5:0.1:10");
        addDefaultParticleEffect("effects.search_success.particle", "VILLAGER_HAPPY:0.5:0.5:0.5:0.1:10");
        addDefaultParticleEffect("effects.large_transfer.sender_particle", "SPELL_WITCH:0.2:0.2:0.2:0.05:10");
        addDefaultParticleEffect("effects.large_transfer.receiver_particle", "TOTEM:0.5:0.5:0.5:0.1:10");
        addDefaultParticleEffect("effects.collect.particle", "VILLAGER_HAPPY:0.3:0.3:0.3:0.05:8");
        
        // Lưu cấu hình nếu có thay đổi
        plugin.saveConfig();
    }

    /**
     * Thêm cấu hình âm thanh mặc định nếu chưa có
     * @param path Đường dẫn cấu hình
     * @param defaultValue Giá trị mặc định (định dạng "SOUND:VOLUME:PITCH")
     */
    private static void addDefaultSoundEffect(String path, String defaultValue) {
        Storage plugin = Storage.getStorage();
        if (!config.contains(path)) {
            config.set(path, defaultValue);
            plugin.getLogger().info("Đã thêm cấu hình mặc định cho âm thanh: " + path);
        }
    }

    /**
     * Thêm cấu hình hiệu ứng hạt mặc định nếu chưa có
     * @param path Đường dẫn cấu hình
     * @param defaultValue Giá trị mặc định (định dạng "PARTICLE:OFFSETX:OFFSETY:OFFSETZ:SPEED:COUNT")
     */
    private static void addDefaultParticleEffect(String path, String defaultValue) {
        Storage plugin = Storage.getStorage();
        if (!config.contains(path)) {
            config.set(path, defaultValue);
            plugin.getLogger().info("Đã thêm cấu hình mặc định cho hiệu ứng hạt: " + path);
        }
    }
}
