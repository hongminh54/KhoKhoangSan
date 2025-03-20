package com.hongminh54.storage.Listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import com.hongminh54.storage.Manager.MineManager;
import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.Chat;
import com.hongminh54.storage.Utils.StatsManager;

public class JoinQuit implements Listener {

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent e) {
        Player p = e.getPlayer();
        
        // Tải dữ liệu thống kê trước
        StatsManager.loadPlayerStats(p);
        
        // Sau đó tải dữ liệu kho
        MineManager.loadPlayerData(p);
        
        // Ghi log để debug
        Storage.getStorage().getLogger().info("Đã tải dữ liệu kho và thống kê cho " + p.getName());

        // Gửi thông báo về trạng thái auto pick up sau 10 giây
        Bukkit.getScheduler().runTaskLater(Storage.getStorage(), () -> {
            if (p.isOnline()) {
                boolean autoPickup = MineManager.isAutoPickup(p);
                String status = autoPickup ? "&ađang bật" : "&cđang tắt";
                p.sendMessage(Chat.colorizewp("&7[&fStorage&7] &eTự động nhặt vật phẩm " + status));
            }
        }, 20 * 10); // 10 giây = 20 ticks/giây * 10
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent e) {
        Player p = e.getPlayer();
        
        // Lưu dữ liệu thống kê trước
        StatsManager.savePlayerStats(p);
        
        // Sau đó lưu dữ liệu kho
        MineManager.savePlayerData(p);
        
        // Xóa dữ liệu khỏi cache
        StatsManager.removeFromCache(p.getName());
        
        // Ghi log để debug
        Storage.getStorage().getLogger().info("Đã lưu dữ liệu kho cho " + p.getName());
    }
}
