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
import com.hongminh54.storage.GUI.GUI;
import com.hongminh54.storage.Listeners.BlockBreak;
import com.hongminh54.storage.Listeners.BlockPlace;
import com.hongminh54.storage.Listeners.Chat;
import com.hongminh54.storage.Listeners.JoinQuit;
import com.hongminh54.storage.Manager.MineManager;
import com.hongminh54.storage.NMS.NMSAssistant;
import com.hongminh54.storage.Placeholder.PAPI;
import com.hongminh54.storage.Utils.File;
import com.hongminh54.storage.Utils.LeaderboardManager;
import com.hongminh54.storage.Utils.UpdateChecker;

import net.xconfig.bukkit.model.SimpleConfigurationManager;

public final class Storage extends JavaPlugin {

    public static Database db;
    private static Storage storage;
    private static boolean WorldGuard;
    private static final List<UpdateChecker> updateCheckers = new ArrayList<>();

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
        
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PAPI().register();
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
        registerEvents(updateChecker, new JoinQuit(), new BlockBreak(), new Chat(), new BlockPlace());
        updateChecker.fetch();
        
        new StorageCMD("storage");
        db = new SQLite(Storage.getStorage());
        db.load();
        MineManager.loadBlocks();
        if (new NMSAssistant().isVersionLessThanOrEqualTo(12)) {
            getLogger().log(Level.WARNING, "Some material can working incorrect way with your version server (" + new NMSAssistant().getNMSVersion() + ")");
            getLogger().log(Level.WARNING, "If material doesn't work, you should go to discord and report to author!");
        }
    }

    @Override
    public void onDisable() {
        for (Player p : getServer().getOnlinePlayers()) {
            com.hongminh54.storage.Utils.StatsManager.savePlayerStats(p);
            MineManager.savePlayerData(p);
        }
        File.saveFiles();
    }


    public void registerEvents(Listener... listeners) {
        Arrays.asList(listeners).forEach(listener -> getServer().getPluginManager().registerEvents(listener, storage));
    }
}
