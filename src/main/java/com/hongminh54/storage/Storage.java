package com.hongminh54.storage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import com.hongminh54.storage.CMD.StorageCMD;
import com.hongminh54.storage.Database.Database;
import com.hongminh54.storage.Database.SQLite;
import com.hongminh54.storage.Events.BlockBreakEvent_;
import com.hongminh54.storage.Events.EventScheduler;
import com.hongminh54.storage.Events.MiningEvent;
import com.hongminh54.storage.GUI.GUI;
import com.hongminh54.storage.Listeners.BlockBreak;
import com.hongminh54.storage.Listeners.BlockPlace;
import com.hongminh54.storage.Listeners.Chat;
import com.hongminh54.storage.Listeners.JoinQuit;
import com.hongminh54.storage.Listeners.PlayerListener;
import com.hongminh54.storage.Manager.MineManager;
import com.hongminh54.storage.NMS.NMSAssistant;
import com.hongminh54.storage.Placeholder.PAPI;
import com.hongminh54.storage.Utils.CacheCoordinator;
import com.hongminh54.storage.Utils.CacheManager;
import com.hongminh54.storage.Utils.File;
import com.hongminh54.storage.Utils.LeaderboardManager;
import com.hongminh54.storage.Utils.PlayerSearchChatHandler;
import com.hongminh54.storage.Utils.StatsManager;
import com.hongminh54.storage.Utils.TransferManager;
import com.hongminh54.storage.Utils.UpdateChecker;

import net.xconfig.bukkit.model.SimpleConfigurationManager;

public final class Storage extends JavaPlugin {

    public static Database db;
    private static Storage storage;
    private static boolean WorldGuard;
    private static final List<UpdateChecker> updateCheckers = new ArrayList<>();

    // Thêm biến để quản lý trạng thái debug
    private boolean debug = false;
    
    /**
     * Kiểm tra xem plugin có đang ở chế độ debug không
     * @return true nếu đang ở chế độ debug
     */
    public boolean isDebug() {
        return debug;
    }
    
    /**
     * Đặt trạng thái debug
     * @param debug trạng thái debug mới
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public static Storage getStorage() {
        return storage;
    }
    
    public static List<UpdateChecker> getUpdateCheckers() {
        return updateCheckers;
    }

    public static boolean isWorldGuardInstalled() {
        return WorldGuard;
    }

    @Override
    public void onLoad() {
        storage = this;
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            WorldGuard = true;
            com.hongminh54.storage.WorldGuard.WorldGuard.register(storage);
            getLogger().log(Level.INFO, "Hook with WorldGuard");
        }
    }

    @Override
    public void onEnable() {
        Bukkit.getConsoleSender().sendMessage("§a╔════════════════════════════════════════════════╗");
        Bukkit.getConsoleSender().sendMessage("§a║             §e§lKHO KHOÁNG SẢN PLUS          §a║");
        Bukkit.getConsoleSender().sendMessage("§a║                                              §a║");
        Bukkit.getConsoleSender().sendMessage("§a║  §f§oPhiên bản: §6v1.0.0                     §a║");
        Bukkit.getConsoleSender().sendMessage("§a║                                              §a║");
        Bukkit.getConsoleSender().sendMessage("§a║  §d§oHỗ trợ phiên bản: §a1.8.8 - 1.20.x      §a║");
        Bukkit.getConsoleSender().sendMessage("§a║  §d§oHỗ trợ Server: §eSpigot, Paper, Purpur  §a║");
        Bukkit.getConsoleSender().sendMessage("§a║                                              §a║");
        Bukkit.getConsoleSender().sendMessage("§a║  §c§oTác giả: §dVoChiDanh, hongminh54, TYBZI §a║");
        Bukkit.getConsoleSender().sendMessage("§a╚════════════════════════════════════════════════╝");
        
        GUI.register(storage);
        SimpleConfigurationManager.register(storage);
        File.loadFiles();
        File.loadGUI();
        File.updateConfig();
        File.updateMessage();
        
        // Khởi tạo hệ thống sự kiện
        MiningEvent.getInstance().loadEventConfig();
        
        // Khởi tạo và bắt đầu lịch trình sự kiện tự động
        EventScheduler.getInstance().loadSchedules();
        
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PAPI().register();
        }
        
        // Khởi tạo hệ thống cache
        try {
            getLogger().info("Đang khởi tạo hệ thống cache...");
            CacheManager.initialize();
            getLogger().info("Hệ thống cache đã được khởi tạo thành công!");
            
            // Khởi tạo hệ thống đồng bộ hóa cache
            CacheCoordinator.initialize();
            getLogger().info("Hệ thống đồng bộ hóa cache đã được khởi tạo thành công!");
        } catch (Exception e) {
            getLogger().severe("Lỗi khi khởi tạo hệ thống cache: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Cập nhật bảng xếp hạng khi server khởi động
        try {
            LeaderboardManager.updateAllLeaderboards();
        } catch (Exception e) {
            getLogger().warning("Không thể cập nhật dữ liệu bảng xếp hạng: " + e.getMessage());
        }

        // Tạo và lưu trữ UpdateChecker
        UpdateChecker updateChecker = new UpdateChecker(storage);
        updateCheckers.add(updateChecker);
        
        // Tạo đối tượng BlockBreakEvent_ và bắt đầu lịch trình dọn dẹp cache
        BlockBreakEvent_ blockBreakEvent = new BlockBreakEvent_();
        blockBreakEvent.scheduleCacheCleanup();
        
        // Đăng ký tất cả các sự kiện
        registerEvents(updateChecker, new JoinQuit(), new BlockBreak(), new Chat(), new BlockPlace(), blockBreakEvent);
        updateChecker.fetch();
        
        new StorageCMD("storage");
        db = new SQLite(Storage.getStorage());
        db.load();
        
        // Khởi tạo bảng lịch sử giao dịch
        try {
            boolean tableCreated = TransferManager.createTransferHistoryTable();
            if (tableCreated) {
                getLogger().info("Đã khởi tạo bảng lịch sử giao dịch thành công.");
            } else {
                getLogger().warning("Không thể khởi tạo bảng lịch sử giao dịch.");
            }
        } catch (Exception e) {
            getLogger().severe("Lỗi khi khởi tạo bảng lịch sử giao dịch: " + e.getMessage());
            e.printStackTrace();
        }
        
        MineManager.loadBlocks();
        if (new NMSAssistant().isVersionLessThanOrEqualTo(12)) {
            getLogger().log(Level.WARNING, "Some material can working incorrect way with your version server (" + new NMSAssistant().getNMSVersion() + ")");
            getLogger().log(Level.WARNING, "If material doesn't work, you should go to discord and report to author!");
        }

        // Đăng ký PlayerListener mới để xử lý sự kiện khi người chơi thoát game
        registerEvents(new PlayerListener(this));
    }

    @Override
    public void onDisable() {
        // Hủy tất cả chat input đang chờ xử lý
        PlayerSearchChatHandler.cancelAllInputs();
        
        // Lưu dữ liệu người chơi một cách đồng bộ để đảm bảo tất cả dữ liệu được lưu trước khi plugin tắt hoàn toàn
        getLogger().info("Đang lưu dữ liệu người chơi trước khi tắt plugin...");
        int playerCount = 0;
        
        for (Player p : getServer().getOnlinePlayers()) {
            try {
                // Sử dụng phương thức đồng bộ để lưu dữ liệu khi tắt plugin
                com.hongminh54.storage.Utils.StatsManager.savePlayerStats(p);
                MineManager.savePlayerData(p);
                playerCount++;
                
                // Xóa cache của người chơi
                CacheManager.removePlayerCache(p);
                // Xóa dữ liệu khỏi cache
                StatsManager.removeFromCache(p.getName());
                // Xóa dữ liệu từ cache bảng xếp hạng
                LeaderboardManager.removePlayerFromCache(p.getName());
            } catch (Exception ex) {
                getLogger().warning("Lỗi khi lưu dữ liệu cho " + p.getName() + ": " + ex.getMessage());
            }
        }
        
        getLogger().info("Đã lưu dữ liệu cho " + playerCount + " người chơi.");
        
        // Dừng sự kiện nếu đang diễn ra
        MiningEvent.getInstance().endEvent();
        
        // Dọn dẹp tài nguyên của scheduler
        EventScheduler.getInstance().dispose();
        
        // Đóng kết nối với cơ sở dữ liệu
        if (db != null) {
            db.closeConnection();
            getLogger().info("Đã đóng kết nối đến cơ sở dữ liệu.");
        }
        
        // Lưu các file cấu hình
        File.saveFiles();
        
        getLogger().info("Plugin Storage đã bị tắt!");
    }


    public void registerEvents(Listener... listeners) {
        Arrays.asList(listeners).forEach(listener -> getServer().getPluginManager().registerEvents(listener, storage));
    }
}
