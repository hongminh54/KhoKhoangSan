package com.hongminh54.storage.Listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.PlayerSearchChatHandler;

/**
 * Xử lý các sự kiện người chơi liên quan đến việc thoát game
 */
public class PlayerListener implements Listener {
    
    private final Storage plugin;
    
    /**
     * Khởi tạo listener
     * @param plugin Plugin chính
     */
    public PlayerListener(Storage plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Xử lý sự kiện khi người chơi thoát game
     * @param event Sự kiện PlayerQuitEvent
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        
        // Chờ 1 tick trước khi xử lý để tránh xung đột
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Hủy tất cả chat input đang chờ xử lý của người chơi này
            PlayerSearchChatHandler.cancelInput(player);
            
            // Đóng tất cả GUI đang mở của người chơi
            if (player.getOpenInventory() != null) {
                player.closeInventory();
            }
            
        }, 1L);
        
        // Log debug nếu cần
        if (plugin.isDebug()) {
            plugin.getLogger().info("Người chơi " + player.getName() + " đã thoát game, đã dọn dẹp tài nguyên");
        }
    }
} 