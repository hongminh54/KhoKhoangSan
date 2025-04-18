package com.hongminh54.storage.CMD;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.hongminh54.storage.GUI.ViewPlayerStorageGUI;
import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.Chat;
import com.hongminh54.storage.Utils.PlayerSearchChatHandler;

/**
 * Lệnh để xem kho của người chơi khác
 */
public class ViewStorageCommand implements CommandExecutor, TabCompleter {
    
    public ViewStorageCommand() {
        // Đăng ký lệnh
        Storage.getStorage().getCommand("viewstorage").setExecutor(this);
        Storage.getStorage().getCommand("viewstorage").setTabCompleter(this);
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        // Chỉ người chơi mới sử dụng được lệnh này
        if (!(sender instanceof Player)) {
            sender.sendMessage(Chat.colorizewp("&cChỉ người chơi mới có thể sử dụng lệnh này!"));
            return true;
        }
        
        Player player = (Player) sender;
        
        // Kiểm tra quyền
        if (!player.hasPermission("storage.view.others")) {
            player.sendMessage(Chat.colorizewp("&cBạn không có quyền xem kho của người chơi khác!"));
            return true;
        }
        
        // Nếu không có tham số, mở giao diện tìm kiếm người chơi
        if (args.length == 0) {
            openPlayerSearch(player);
            return true;
        }
        
        // Mở kho của người chơi cụ thể
        String targetName = args[0];
        openPlayerStorage(player, targetName);
        
        return true;
    }
    
    /**
     * Mở giao diện tìm kiếm người chơi
     */
    private void openPlayerSearch(Player player) {
        player.sendMessage(Chat.colorizewp("&aVui lòng nhập tên người chơi bạn muốn xem kho:"));
        player.sendMessage(Chat.colorizewp("&7(Nhập &e'cancel'&7 để hủy tìm kiếm)"));
        
        // Đăng ký chat handler
        PlayerSearchChatHandler.startChatInput(player, "&aXem kho của người chơi khác", input -> {
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(Chat.colorizewp("&cĐã hủy tìm kiếm."));
                return;
            }
            
            // Mở kho của người chơi được nhập
            openPlayerStorage(player, input);
        });
    }
    
    /**
     * Mở kho của người chơi cụ thể
     */
    private void openPlayerStorage(Player viewer, String targetName) {
        // Kiểm tra nhanh xem người chơi có online không
        Player targetPlayer = Bukkit.getPlayer(targetName);
        
        if (targetPlayer != null && targetPlayer.isOnline()) {
            // Người chơi online
            viewer.openInventory(new ViewPlayerStorageGUI(viewer, targetPlayer.getName()).getInventory());
        } else {
            // Người chơi offline, tạo GUI từ dữ liệu database
            viewer.openInventory(new ViewPlayerStorageGUI(viewer, targetName).getInventory());
        }
    }
    
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            // Hiển thị danh sách người chơi online
            List<String> players = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                // Chỉ hiển thị người chơi có tên khớp với đầu vào
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    players.add(player.getName());
                }
            }
            return players;
        }
        return new ArrayList<>();
    }
} 