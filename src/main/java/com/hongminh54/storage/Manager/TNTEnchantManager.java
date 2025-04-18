package com.hongminh54.storage.Manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.Chat;
import com.hongminh54.storage.Utils.File;

/**
 * Manager quản lý phù phép TNT trên cúp
 */
public class TNTEnchantManager {
    
    private static final Map<UUID, Long> cooldownMap = new HashMap<>();
    private static final int DEFAULT_COOLDOWN = 500; // 0.5 giây để tránh lag
    
    /**
     * Kiểm tra xem một item có phù phép TNT hay không dựa vào lore
     * @param item ItemStack cần kiểm tra
     * @return Cấp độ phù phép TNT (0 nếu không có)
     */
    public static int getEnchantLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return 0;
        }
        
        List<String> lore = item.getItemMeta().getLore();
        if (lore == null) return 0;
        
        String enchantPrefix = Chat.colorizewp(File.getConfig().getString("tnt_enchant.lore_prefix", "&c&lTNT"));
        
        for (String line : lore) {
            if (line.contains(enchantPrefix)) {
                if (line.contains("III")) return 3;
                if (line.contains("II")) return 2;
                if (line.contains("I")) return 1;
            }
        }
        
        return 0;
    }
    
    /**
     * Kiểm tra xem một item có phải là cúp hay không
     * @param item ItemStack cần kiểm tra
     * @return true nếu là cúp, false nếu không phải
     */
    public static boolean isPickaxe(ItemStack item) {
        if (item == null) return false;
        
        String typeName = item.getType().name();
        
        // Kiểm tra trực tiếp tất cả các loại cúp có thể có trong phiên bản 1.12.2 và mới hơn
        return typeName.equals("WOOD_PICKAXE") || 
               typeName.equals("STONE_PICKAXE") || 
               typeName.equals("IRON_PICKAXE") || 
               typeName.equals("GOLD_PICKAXE") || 
               typeName.equals("DIAMOND_PICKAXE") || 
               typeName.equals("NETHERITE_PICKAXE") || // Phiên bản mới hơn
               typeName.endsWith("_PICKAXE"); // Để tương thích với các phiên bản khác
    }
    
    /**
     * Thêm phù phép TNT vào cúp
     * @param item Cúp cần thêm phù phép
     * @param level Cấp độ phù phép (1-3)
     * @return Item đã được thêm phù phép
     */
    public static ItemStack addTNTEnchant(ItemStack item, int level) {
        if (!isPickaxe(item)) {
            return item;
        }
        
        if (level < 1 || level > 3) {
            level = 1;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        if (lore == null) lore = new ArrayList<>();
        
        // Xóa phù phép TNT cũ nếu có
        lore.removeIf(line -> line.contains(Chat.colorizewp(File.getConfig().getString("tnt_enchant.lore_prefix", "&c&lTNT"))));
        
        // Thêm phù phép mới
        String enchantText = Chat.colorizewp(File.getConfig().getString("tnt_enchant.lore_prefix", "&c&lTNT") + " " + 
                getRomanNumeral(level));
        lore.add(enchantText);
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        if (Storage.getStorage().isDebug()) {
            Storage.getStorage().getLogger().info("Đã thêm phù phép TNT " + level + " vào cúp: " + item.getType().name());
        }
        
        return item;
    }
    
    /**
     * Xóa phù phép TNT khỏi item
     * @param item Item cần xóa phù phép
     * @return Item sau khi đã xóa phù phép
     */
    public static ItemStack removeTNTEnchant(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return item;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        
        List<String> lore = meta.getLore();
        if (lore == null) return item;
        
        // Xóa phù phép TNT
        lore.removeIf(line -> line.contains(Chat.colorizewp(File.getConfig().getString("tnt_enchant.lore_prefix", "&c&lTNT"))));
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Kiểm tra cooldown để tránh lag server
     * @param player Người chơi cần kiểm tra
     * @return true nếu người chơi có thể sử dụng phù phép, false nếu đang trong cooldown
     */
    public static boolean checkCooldown(Player player) {
        if (!cooldownMap.containsKey(player.getUniqueId())) {
            cooldownMap.put(player.getUniqueId(), System.currentTimeMillis());
            return true;
        }
        
        long lastUse = cooldownMap.get(player.getUniqueId());
        long cooldownTime = File.getConfig().getInt("tnt_enchant.cooldown", DEFAULT_COOLDOWN);
        
        if (System.currentTimeMillis() - lastUse >= cooldownTime) {
            cooldownMap.put(player.getUniqueId(), System.currentTimeMillis());
            return true;
        }
        
        return false;
    }
    
    /**
     * Xử lý phá khối diện rộng khi có enchant TNT
     * @param player Người chơi sử dụng cúp
     * @param block Block gốc bị phá
     * @param level Cấp độ enchant TNT
     * @return Danh sách các block đã được phá
     */
    public static List<Block> handleTNTBreak(Player player, Block block, int level) {
        // Kiểm tra cooldown để tránh lag
        if (!checkCooldown(player)) {
            return Collections.singletonList(block);
        }
        
        // Lấy kích thước vùng phá dựa trên cấp độ phù phép
        int radius = level;
        
        // Thu thập tất cả các block trong phạm vi
        List<Block> blocksToBreak = new ArrayList<>();
        blocksToBreak.add(block); // Luôn thêm block chính
        
        Location center = block.getLocation();
        
        // Quét 3x3 xung quanh vị trí đào (với level 3 là 3x3x3)
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    // Bỏ qua block trung tâm vì đã thêm rồi
                    if (x == 0 && y == 0 && z == 0) continue;
                    
                    Block relativeBlock = center.clone().add(x, y, z).getBlock();
                    
                    // Phá tất cả các block có thể khai thác được
                    if (MineManager.checkBreak(relativeBlock)) {
                        blocksToBreak.add(relativeBlock);
                    }
                }
            }
        }
        
        // In thông tin debug nếu được kích hoạt và có nhiều hơn 1 block
        if (Storage.getStorage().isDebug() && blocksToBreak.size() > 1) {
            Storage.getStorage().getLogger().info("TNT Enchant: Phá " + blocksToBreak.size() + " block với cấp độ " + level);
        }
        
        // Giới hạn số lượng block để tránh lag
        int maxBlocks = File.getConfig().getInt("tnt_enchant.max_blocks_per_break", 27);
        if (blocksToBreak.size() > maxBlocks) {
            blocksToBreak = blocksToBreak.subList(0, maxBlocks);
        }
        
        return blocksToBreak;
    }
    
    /**
     * Chuyển đổi số thành chữ số La Mã
     * @param number Số cần chuyển đổi
     * @return Chuỗi La Mã tương ứng
     */
    private static String getRomanNumeral(int number) {
        switch (number) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            default: return String.valueOf(number);
        }
    }
} 