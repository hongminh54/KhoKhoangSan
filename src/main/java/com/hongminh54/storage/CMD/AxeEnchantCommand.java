package com.hongminh54.storage.CMD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import com.hongminh54.storage.Manager.AxeEnchantManager;
import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.Chat;

/**
 * Lệnh để thêm hoặc xóa phù phép cho rìu
 */
public class AxeEnchantCommand implements CommandExecutor, TabCompleter {
    
    private static final List<String> ENCHANT_TYPES = Arrays.asList(
            AxeEnchantManager.TREE_CUTTER,
            AxeEnchantManager.LEAF_COLLECTOR,
            AxeEnchantManager.REGROWTH,
            AxeEnchantManager.AUTO_PLANT
    );
    
    private static final List<String> ENCHANT_NAMES = Arrays.asList(
            "tree_cutter (Chặt Cây)",
            "leaf_collector (Thu Lá)",
            "regrowth (Tái Sinh)",
            "auto_plant (Tự Trồng)"
    );
    
    public AxeEnchantCommand() {
        Storage.getStorage().getCommand("axeenchant").setExecutor(this);
        Storage.getStorage().getCommand("axeenchant").setTabCompleter(this);
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (!(sender instanceof Player) && args.length < 2) {
            sender.sendMessage(Chat.colorizewp("&cLệnh này chỉ có thể được sử dụng bởi người chơi!"));
            return true;
        }
        
        // Hiển thị hướng dẫn sử dụng
        if (args.length == 0) {
            sendUsageMessage(sender);
            return true;
        }
        
        String action = args[0].toLowerCase();
        
        // Hiển thị danh sách phù phép
        if (action.equals("list")) {
            sendEnchantList(sender);
            return true;
        }
        
        // Xác định người chơi mục tiêu dựa trên tham số lệnh
        Player user = null;
        String targetPlayerName = null;
        
        // Thêm hoặc xóa phù phép
        if (action.equals("add") || action.equals("remove")) {
            // Kiểm tra số lượng tham số
            if ((action.equals("add") && args.length < 2) || 
                (action.equals("remove") && args.length < 2)) {
                sendUsageMessage(sender);
                return true;
            }
            
            // Xác định loại phù phép
            String enchantType = args.length >= 2 ? args[1].toLowerCase() : "";
            
            // Kiểm tra loại phù phép có hợp lệ không
            if (!ENCHANT_TYPES.contains(enchantType)) {
                sender.sendMessage(Chat.colorizewp("&cLoại phù phép không hợp lệ! Sử dụng /axeenchant list để xem danh sách."));
                return true;
            }
            
            // Lấy cấp độ phù phép (chỉ áp dụng cho lệnh add)
            int level = 1;
            if (action.equals("add") && args.length >= 3) {
                try {
                    level = Integer.parseInt(args[2]);
                    if (level < 1 || level > 3) {
                        sender.sendMessage(Chat.colorizewp("&cCấp độ phù phép phải từ 1 đến 3!"));
                        return true;
                    }
                } catch (NumberFormatException e) {
                    // Nếu không phải số, có thể đây là tên người chơi
                    targetPlayerName = args[2];
                }
            }
            
            // Xác định người chơi mục tiêu
            if (targetPlayerName == null && action.equals("add") && args.length >= 4 && sender.hasPermission("storage.axe.admin")) {
                targetPlayerName = args[3];
            } else if (targetPlayerName == null && action.equals("remove") && args.length >= 3 && sender.hasPermission("storage.axe.admin")) {
                targetPlayerName = args[2];
            }
            
            // Nếu có tên người chơi mục tiêu và người gửi có quyền admin
            if (targetPlayerName != null && sender.hasPermission("storage.axe.admin")) {
                user = Bukkit.getPlayer(targetPlayerName);
                if (user == null) {
                    sender.sendMessage(Chat.colorizewp("&cNgười chơi &e" + targetPlayerName + " &ckhông tồn tại hoặc không trực tuyến!"));
                    return true;
                }
            } else {
                // Nếu không xác định được người chơi mục tiêu, sử dụng người gửi lệnh
                if (sender instanceof Player) {
                    user = (Player) sender;
                } else {
                    sender.sendMessage(Chat.colorizewp("&cBạn phải là người chơi hoặc chỉ định một người chơi!"));
                    return true;
                }
            }
            
            // Kiểm tra quyền
            if (!sender.hasPermission("storage.axe.enchant")) {
                sender.sendMessage(Chat.colorizewp("&cBạn không có quyền sử dụng lệnh này!"));
                return true;
            }
            
            // Lấy item trên tay người chơi
            ItemStack handItem = user.getInventory().getItemInMainHand();
            
            // Kiểm tra xem item có phải là rìu không
            if (!AxeEnchantManager.isAxe(handItem)) {
                sender.sendMessage(Chat.colorizewp("&c" + (sender == user ? "Bạn" : user.getName()) + " phải cầm rìu trên tay để sử dụng lệnh này!"));
                return true;
            }
            
            // Xử lý thêm/xóa phù phép
            if (action.equals("add")) {
                // Thêm phù phép
                ItemStack enchantedItem = AxeEnchantManager.addAxeEnchant(handItem, enchantType, level);
                user.getInventory().setItemInMainHand(enchantedItem);
                
                // Gửi thông báo
                String enchantName = getEnchantDisplayName(enchantType);
                sender.sendMessage(Chat.colorizewp("&aĐã thêm phù phép &e" + enchantName + " " + getRomanLevel(level) + 
                                             "&a vào rìu" + (sender != user ? " của " + user.getName() : "") + "!"));
            } else {
                // Kiểm tra xem item có phù phép này không
                if (AxeEnchantManager.getEnchantLevel(handItem, enchantType) == 0) {
                    sender.sendMessage(Chat.colorizewp("&cRìu này không có phù phép &e" + getEnchantDisplayName(enchantType) + "&c!"));
                    return true;
                }
                
                // Xóa phù phép
                ItemStack normalItem = AxeEnchantManager.removeAxeEnchant(handItem, enchantType);
                user.getInventory().setItemInMainHand(normalItem);
                
                // Gửi thông báo
                String enchantName = getEnchantDisplayName(enchantType);
                sender.sendMessage(Chat.colorizewp("&aĐã xóa phù phép &e" + enchantName + 
                                             "&a khỏi rìu" + (sender != user ? " của " + user.getName() : "") + "!"));
            }
            
            return true;
        }
        
        // Lệnh không hợp lệ
        sendUsageMessage(sender);
        return true;
    }
    
    /**
     * Gửi thông báo hướng dẫn sử dụng
     * @param sender Người gửi lệnh
     */
    private void sendUsageMessage(CommandSender sender) {
        sender.sendMessage(Chat.colorizewp("&e=== Phù phép đặc biệt (AXE) ==="));
        sender.sendMessage(Chat.colorizewp("&a/axeenchant list &7- Xem danh sách các loại phù phép"));
        sender.sendMessage(Chat.colorizewp("&a/axeenchant add &e<loại> [cấp độ] [người chơi] &7- Thêm phù phép vào rìu"));
        sender.sendMessage(Chat.colorizewp("&a/axeenchant remove &e<loại> [người chơi] &7- Xóa phù phép khỏi rìu"));
        sender.sendMessage(Chat.colorizewp("&eCác loại phù phép: tree_cutter, leaf_collector, regrowth, auto_plant"));
        sender.sendMessage(Chat.colorizewp("&eCấp độ: từ 1 đến 3 (mặc định là 1)"));
    }
    
    /**
     * Gửi danh sách các loại phù phép
     * @param sender Người gửi lệnh
     */
    private void sendEnchantList(CommandSender sender) {
        sender.sendMessage(Chat.colorizewp("&e=== Danh sách phù phép rìu ==="));
        for (String enchantName : ENCHANT_NAMES) {
            sender.sendMessage(Chat.colorizewp("&a- &e" + enchantName));
        }
    }
    
    /**
     * Lấy tên hiển thị cho loại phù phép
     * @param enchantType Loại phù phép
     * @return Tên hiển thị
     */
    private String getEnchantDisplayName(String enchantType) {
        switch (enchantType) {
            case AxeEnchantManager.TREE_CUTTER: return "Chặt Cây";
            case AxeEnchantManager.LEAF_COLLECTOR: return "Thu Lá";
            case AxeEnchantManager.REGROWTH: return "Tái Sinh";
            case AxeEnchantManager.AUTO_PLANT: return "Tự Trồng";
            default: return enchantType;
        }
    }
    
    /**
     * Chuyển đổi cấp độ số thành chữ số La Mã
     * @param level Cấp độ
     * @return Chữ số La Mã
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
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Gợi ý lệnh add, remove, list
            String[] commands = {"add", "remove", "list"};
            for (String command : commands) {
                if (command.startsWith(args[0].toLowerCase())) {
                    completions.add(command);
                }
            }
        } else if (args.length == 2) {
            // Nếu lệnh là add hoặc remove, gợi ý loại phù phép
            if (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove")) {
                for (String enchantType : ENCHANT_TYPES) {
                    if (enchantType.startsWith(args[1].toLowerCase())) {
                        completions.add(enchantType);
                    }
                }
            }
        } else if (args.length == 3) {
            // Nếu lệnh là add, gợi ý cấp độ phù phép
            if (args[0].equalsIgnoreCase("add")) {
                for (int i = 1; i <= 3; i++) {
                    completions.add(String.valueOf(i));
                }
            }
            
            // Nếu người chơi có quyền admin, cũng gợi ý tên người chơi
            if (sender.hasPermission("storage.axe.admin")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("add") && sender.hasPermission("storage.axe.admin")) {
            // Nếu lệnh là add và đã có loại phù phép + cấp độ, gợi ý tên người chơi
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[3].toLowerCase())) {
                    completions.add(player.getName());
                }
            }
        }
        
        return completions;
    }
} 