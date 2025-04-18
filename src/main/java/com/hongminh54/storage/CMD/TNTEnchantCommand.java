package com.hongminh54.storage.CMD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import com.hongminh54.storage.Manager.TNTEnchantManager;
import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.Chat;
import com.hongminh54.storage.Utils.File;

/**
 * Lớp quản lý lệnh /kphuphep để thêm hoặc xóa phù phép TNT
 */
public class TNTEnchantCommand implements CommandExecutor, TabCompleter {
    
    private static final String PERMISSION = "storage.tnt.enchant";
    private static final String PERMISSION_ADMIN = "storage.tnt.admin";
    
    /**
     * Đăng ký lệnh với server
     */
    public TNTEnchantCommand() {
        Objects.requireNonNull(Storage.getStorage().getCommand("kphuphep")).setExecutor(this);
        Objects.requireNonNull(Storage.getStorage().getCommand("kphuphep")).setTabCompleter(this);
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Chat.colorizewp("&cLệnh này chỉ có thể được sử dụng bởi người chơi!"));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(Chat.colorizewp(Objects.requireNonNull(File.getConfig().getString("messages.no_permission", "&cBạn không có quyền sử dụng lệnh này!"))));
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "help":
                sendHelp(player);
                break;
                
            case "add":
                handleAddCommand(player, args);
                break;
                
            case "remove":
                handleRemoveCommand(player, args);
                break;
                
            default:
                player.sendMessage(Chat.colorizewp("&cLệnh phụ không hợp lệ! Sử dụng /kphuphep help để xem trợ giúp."));
                break;
        }
        
        return true;
    }
    
    /**
     * Gửi trợ giúp về lệnh cho người chơi
     * 
     * @param player Người chơi nhận trợ giúp
     */
    private void sendHelp(Player player) {
        player.sendMessage(Chat.colorizewp("&e====== &6Phù Phép Đặc Biệt (TNT) &e======"));
        player.sendMessage(Chat.colorizewp("&6/kphuphep help &f- Hiển thị trợ giúp"));
        player.sendMessage(Chat.colorizewp("&6/kphuphep add [cấp độ] &f- Thêm phù phép TNT vào cúp cầm trên tay"));
        player.sendMessage(Chat.colorizewp("&6/kphuphep remove &f- Xóa phù phép TNT khỏi cúp cầm trên tay"));
        
        if (player.hasPermission(PERMISSION_ADMIN)) {
            player.sendMessage(Chat.colorizewp("&6/kphuphep add [cấp độ] [người chơi] &f- Thêm phù phép TNT cho người chơi khác"));
            player.sendMessage(Chat.colorizewp("&6/kphuphep remove [người chơi] &f- Xóa phù phép TNT của người chơi khác"));
        }
    }
    
    /**
     * Xử lý lệnh thêm phù phép TNT
     * 
     * @param player Người chơi sử dụng lệnh
     * @param args Tham số lệnh
     */
    private void handleAddCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Chat.colorizewp("&cSử dụng: /kphuphep add [cấp độ] [người chơi]"));
            return;
        }
        
        int level;
        try {
            level = Integer.parseInt(args[1]);
            if (level < 1 || level > 3) {
                player.sendMessage(Chat.colorizewp("&cCấp độ phù phép phải từ 1 đến 3!"));
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Chat.colorizewp("&cCấp độ phải là một số nguyên từ 1 đến 3!"));
            return;
        }
        
        // Xử lý trường hợp thêm phù phép cho người chơi khác
        if (args.length >= 3 && player.hasPermission(PERMISSION_ADMIN)) {
            Player targetPlayer = Bukkit.getPlayer(args[2]);
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                player.sendMessage(Chat.colorizewp("&cNgười chơi không trực tuyến hoặc không tồn tại!"));
                return;
            }
            
            ItemStack handItem = targetPlayer.getInventory().getItemInMainHand();
            if (!TNTEnchantManager.isPickaxe(handItem)) {
                player.sendMessage(Chat.colorizewp("&cNgười chơi &e" + targetPlayer.getName() + " &ckhông cầm cúp trên tay!"));
                return;
            }
            
            targetPlayer.getInventory().setItemInMainHand(TNTEnchantManager.addTNTEnchant(handItem, level));
            player.sendMessage(Chat.colorizewp("&aĐã thêm phù phép TNT " + getRomanLevel(level) + " cho cúp của &e" + targetPlayer.getName() + "&a!"));
            targetPlayer.sendMessage(Chat.colorizewp("&aCúp của bạn đã được &e" + player.getName() + " &athêm phù phép TNT " + getRomanLevel(level) + "!"));
            return;
        }
        
        // Xử lý trường hợp thêm phù phép cho bản thân
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (!TNTEnchantManager.isPickaxe(handItem)) {
            player.sendMessage(Chat.colorizewp("&cBạn phải cầm cúp trên tay để thêm phù phép TNT!"));
            return;
        }
        
        player.getInventory().setItemInMainHand(TNTEnchantManager.addTNTEnchant(handItem, level));
        player.sendMessage(Chat.colorizewp("&aĐã thêm phù phép TNT " + getRomanLevel(level) + " vào cúp của bạn!"));
    }
    
    /**
     * Xử lý lệnh xóa phù phép TNT
     * 
     * @param player Người chơi sử dụng lệnh
     * @param args Tham số lệnh
     */
    private void handleRemoveCommand(Player player, String[] args) {
        // Xử lý trường hợp xóa phù phép của người chơi khác
        if (args.length >= 2 && player.hasPermission(PERMISSION_ADMIN)) {
            Player targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                player.sendMessage(Chat.colorizewp("&cNgười chơi không trực tuyến hoặc không tồn tại!"));
                return;
            }
            
            ItemStack handItem = targetPlayer.getInventory().getItemInMainHand();
            if (!TNTEnchantManager.isPickaxe(handItem)) {
                player.sendMessage(Chat.colorizewp("&cNgười chơi &e" + targetPlayer.getName() + " &ckhông cầm cúp trên tay!"));
                return;
            }
            
            if (TNTEnchantManager.getEnchantLevel(handItem) == 0) {
                player.sendMessage(Chat.colorizewp("&cCúp của &e" + targetPlayer.getName() + " &ckhông có phù phép TNT!"));
                return;
            }
            
            targetPlayer.getInventory().setItemInMainHand(TNTEnchantManager.removeTNTEnchant(handItem));
            player.sendMessage(Chat.colorizewp("&aĐã xóa phù phép TNT khỏi cúp của &e" + targetPlayer.getName() + "&a!"));
            targetPlayer.sendMessage(Chat.colorizewp("&aPhù phép TNT đã bị xóa khỏi cúp của bạn bởi &e" + player.getName() + "&a!"));
            return;
        }
        
        // Xử lý trường hợp xóa phù phép của bản thân
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (!TNTEnchantManager.isPickaxe(handItem)) {
            player.sendMessage(Chat.colorizewp("&cBạn phải cầm cúp trên tay để xóa phù phép TNT!"));
            return;
        }
        
        if (TNTEnchantManager.getEnchantLevel(handItem) == 0) {
            player.sendMessage(Chat.colorizewp("&cCúp này không có phù phép TNT!"));
            return;
        }
        
        player.getInventory().setItemInMainHand(TNTEnchantManager.removeTNTEnchant(handItem));
        player.sendMessage(Chat.colorizewp("&aĐã xóa phù phép TNT khỏi cúp của bạn!"));
    }
    
    /**
     * Chuyển đổi cấp độ số thành chữ số La Mã
     * 
     * @param level Cấp độ cần chuyển đổi
     * @return Chuỗi La Mã tương ứng
     */
    private String getRomanLevel(int level) {
        switch (level) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            default: return String.valueOf(level);
        }
    }
    
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.addAll(Arrays.asList("help", "add", "remove"));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("add")) {
                completions.addAll(Arrays.asList("1", "2", "3"));
            } else if (args[0].equalsIgnoreCase("remove") && sender.hasPermission(PERMISSION_ADMIN)) {
                Bukkit.getOnlinePlayers().forEach(player -> completions.add(player.getName()));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("add") && sender.hasPermission(PERMISSION_ADMIN)) {
            Bukkit.getOnlinePlayers().forEach(player -> completions.add(player.getName()));
        }
        
        return completions;
    }
} 