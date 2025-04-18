package com.hongminh54.storage.Manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.Chat;
import com.hongminh54.storage.Utils.File;

/**
 * Manager quản lý các phù phép cho rìu
 */
public class AxeEnchantManager {
    
    // Danh sách phù phép hỗ trợ
    public static final String TREE_CUTTER = "tree_cutter";
    public static final String REGROWTH = "regrowth";
    public static final String LEAF_COLLECTOR = "leaf_collector";
    public static final String AUTO_PLANT = "auto_plant";
    
    // Cooldown để tránh lag
    private static final Map<UUID, Map<String, Long>> cooldownMap = new HashMap<>();
    private static final int DEFAULT_COOLDOWN = 1000; // 1 giây để tránh lag
    private static final Random random = new Random();
    
    /**
     * Kiểm tra xem một item có phải là rìu hay không
     * @param item ItemStack cần kiểm tra
     * @return true nếu là rìu, false nếu không phải
     */
    public static boolean isAxe(ItemStack item) {
        if (item == null) return false;
        
        String typeName = item.getType().name();
        
        // Kiểm tra trực tiếp tất cả các loại rìu có thể có
        return typeName.equals("WOOD_AXE") || 
               typeName.equals("STONE_AXE") || 
               typeName.equals("IRON_AXE") || 
               typeName.equals("GOLD_AXE") || 
               typeName.equals("DIAMOND_AXE") || 
               typeName.equals("NETHERITE_AXE") || // Phiên bản mới hơn
               typeName.endsWith("_AXE"); // Để tương thích với các phiên bản khác
    }
    
    /**
     * Kiểm tra xem một block có phải là khối gỗ không
     * @param block Block cần kiểm tra
     * @return true nếu là khối gỗ, false nếu không phải
     */
    public static boolean isWood(Block block) {
        if (block == null) return false;
        
        String typeName = block.getType().name();
        
        return typeName.endsWith("_LOG") || 
               typeName.equals("LOG") ||
               typeName.equals("LOG_2") ||
               typeName.equals("WOOD") ||
               typeName.endsWith("_WOOD") ||
               typeName.equals("STRIPPED_OAK_LOG") ||
               typeName.equals("STRIPPED_SPRUCE_LOG") ||
               typeName.equals("STRIPPED_BIRCH_LOG") ||
               typeName.equals("STRIPPED_JUNGLE_LOG") ||
               typeName.equals("STRIPPED_ACACIA_LOG") ||
               typeName.equals("STRIPPED_DARK_OAK_LOG") ||
               typeName.contains("STEM"); // Cho warped/crimson stems
    }
    
    /**
     * Kiểm tra xem một block có phải là lá cây không
     * @param block Block cần kiểm tra
     * @return true nếu là lá cây, false nếu không phải
     */
    public static boolean isLeaves(Block block) {
        if (block == null) return false;
        
        String typeName = block.getType().name();
        
        return typeName.equals("LEAVES") || 
               typeName.equals("LEAVES_2") ||
               typeName.endsWith("_LEAVES");
    }
    
    /**
     * Kiểm tra xem một block có phải là đất hoặc cỏ không
     * @param block Block cần kiểm tra
     * @return true nếu là đất hoặc cỏ, false nếu không phải
     */
    public static boolean isSoil(Block block) {
        if (block == null) return false;
        
        String typeName = block.getType().name();
        
        return typeName.equals("DIRT") || 
               typeName.equals("GRASS") ||
               typeName.equals("GRASS_BLOCK") ||
               typeName.equals("PODZOL") ||
               typeName.equals("COARSE_DIRT") ||
               typeName.equals("FARMLAND");
    }
    
    /**
     * Kiểm tra loại cây dựa vào khối gỗ
     * @param woodType Tên của khối gỗ
     * @return TreeType tương ứng
     */
    public static TreeType getTreeType(String woodType) {
        woodType = woodType.toUpperCase();
        
        if (woodType.contains("OAK")) return TreeType.TREE;
        if (woodType.contains("SPRUCE")) return TreeType.REDWOOD;
        if (woodType.contains("BIRCH")) return TreeType.BIRCH;
        if (woodType.contains("JUNGLE")) return TreeType.JUNGLE;
        if (woodType.contains("ACACIA")) return TreeType.ACACIA;
        if (woodType.contains("DARK_OAK")) return TreeType.DARK_OAK;
        
        // Nether wood types (phiên bản mới, có thể không tồn tại trong server cũ)
        try {
            if (woodType.contains("CRIMSON")) {
                // Thử với reflection vì không phải tất cả các phiên bản đều có loại cây này
                return (TreeType) TreeType.class.getField("CRIMSON_FUNGUS").get(null);
            }
            if (woodType.contains("WARPED")) {
                // Thử với reflection vì không phải tất cả các phiên bản đều có loại cây này
                return (TreeType) TreeType.class.getField("WARPED_FUNGUS").get(null);
            }
        } catch (Exception e) {
            // Loại cây không tồn tại trong phiên bản này, mặc định về OAK
            Storage.getStorage().getLogger().warning("TreeType không hỗ trợ loại cây " + woodType + " trong phiên bản này, sử dụng mặc định.");
        }
        
        // Mặc định là OAK
        return TreeType.TREE;
    }
    
    /**
     * Lấy cấp độ phù phép của một loại phù phép cụ thể
     * @param item ItemStack cần kiểm tra
     * @param enchantType Loại phù phép (TREE_CUTTER, REGROWTH, LEAF_COLLECTOR, AUTO_PLANT)
     * @return Cấp độ phù phép (0 nếu không có)
     */
    public static int getEnchantLevel(ItemStack item, String enchantType) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return 0;
        }
        
        List<String> lore = item.getItemMeta().getLore();
        if (lore == null) return 0;
        
        String configPath = "axe_enchant." + enchantType + ".lore_prefix";
        String defaultPrefix = "&a&l" + getDefaultPrefix(enchantType);
        String enchantPrefix = Chat.colorizewp(File.getConfig().getString(configPath, defaultPrefix));
        
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
     * Lấy tiền tố mặc định cho phù phép
     * @param enchantType Loại phù phép
     * @return Tiền tố mặc định
     */
    private static String getDefaultPrefix(String enchantType) {
        switch (enchantType) {
            case TREE_CUTTER: return "Chặt Cây";
            case REGROWTH: return "Tái Sinh";
            case LEAF_COLLECTOR: return "Thu Lá";
            case AUTO_PLANT: return "Tự Trồng";
            default: return enchantType;
        }
    }
    
    /**
     * Thêm phù phép vào rìu
     * @param item Rìu cần thêm phù phép
     * @param enchantType Loại phù phép
     * @param level Cấp độ phù phép (1-3)
     * @return Item đã được thêm phù phép
     */
    public static ItemStack addAxeEnchant(ItemStack item, String enchantType, int level) {
        if (!isAxe(item)) {
            return item;
        }
        
        if (level < 1 || level > 3) {
            level = 1;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        if (lore == null) lore = new ArrayList<>();
        
        // Xóa phù phép cũ cùng loại nếu có
        String configPath = "axe_enchant." + enchantType + ".lore_prefix";
        String defaultPrefix = "&a&l" + getDefaultPrefix(enchantType);
        String enchantPrefix = Chat.colorizewp(File.getConfig().getString(configPath, defaultPrefix));
        
        lore.removeIf(line -> line.contains(enchantPrefix));
        
        // Thêm phù phép mới
        String enchantText = Chat.colorizewp(enchantPrefix + " " + getRomanNumeral(level));
        lore.add(enchantText);
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        if (Storage.getStorage().isDebug()) {
            Storage.getStorage().getLogger().info("Đã thêm phù phép " + enchantType + " " + level + " vào rìu: " + item.getType().name());
        }
        
        return item;
    }
    
    /**
     * Xóa phù phép khỏi item
     * @param item Item cần xóa phù phép
     * @param enchantType Loại phù phép cần xóa
     * @return Item sau khi đã xóa phù phép
     */
    public static ItemStack removeAxeEnchant(ItemStack item, String enchantType) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return item;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        
        List<String> lore = meta.getLore();
        if (lore == null) return item;
        
        // Xóa phù phép cụ thể
        String configPath = "axe_enchant." + enchantType + ".lore_prefix";
        String defaultPrefix = "&a&l" + getDefaultPrefix(enchantType);
        String enchantPrefix = Chat.colorizewp(File.getConfig().getString(configPath, defaultPrefix));
        
        lore.removeIf(line -> line.contains(enchantPrefix));
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Kiểm tra cooldown để tránh lag server
     * @param player Người chơi cần kiểm tra
     * @param enchantType Loại phù phép cần kiểm tra
     * @return true nếu người chơi có thể sử dụng phù phép, false nếu đang trong cooldown
     */
    public static boolean checkCooldown(Player player, String enchantType) {
        UUID playerUUID = player.getUniqueId();
        
        // Khởi tạo map cooldown cho người chơi nếu chưa có
        cooldownMap.putIfAbsent(playerUUID, new HashMap<>());
        Map<String, Long> playerCooldowns = cooldownMap.get(playerUUID);
        
        if (!playerCooldowns.containsKey(enchantType)) {
            playerCooldowns.put(enchantType, System.currentTimeMillis());
            return true;
        }
        
        long lastUse = playerCooldowns.get(enchantType);
        long cooldownTime = File.getConfig().getInt("axe_enchant." + enchantType + ".cooldown", DEFAULT_COOLDOWN);
        
        if (System.currentTimeMillis() - lastUse >= cooldownTime) {
            playerCooldowns.put(enchantType, System.currentTimeMillis());
            return true;
        }
        
        return false;
    }
    
    /**
     * Xử lý phù phép Tree Cutter (chặt toàn bộ cây)
     * @param player Người chơi sử dụng rìu
     * @param block Block gỗ bắt đầu
     * @param level Cấp độ phù phép
     * @return Danh sách các khối gỗ sẽ chặt
     */
    public static List<Block> handleTreeCutter(Player player, Block block, int level) {
        // Kiểm tra cooldown để tránh lag
        if (!checkCooldown(player, TREE_CUTTER)) {
            return Collections.singletonList(block);
        }
        
        // Thiết lập phạm vi dựa trên cấp độ phù phép
        int maxHeight = 10 + (level * 5); // Level 1: 15, Level 2: 20, Level 3: 25
        
        // Thu thập các khối gỗ trong cây
        List<Block> blocksToBreak = new ArrayList<>();
        blocksToBreak.add(block); // Luôn thêm block chính
        
        // Thu thập các khối gỗ còn lại của cây
        collectWoodBlocks(block, blocksToBreak, maxHeight);
        
        if (Storage.getStorage().isDebug() && blocksToBreak.size() > 1) {
            Storage.getStorage().getLogger().info("Tree Cutter: Chặt " + blocksToBreak.size() + " block với cấp độ " + level);
        }
        
        // Giới hạn số lượng block để tránh lag
        int maxBlocks = File.getConfig().getInt("axe_enchant.tree_cutter.max_blocks_per_break", 64);
        if (blocksToBreak.size() > maxBlocks) {
            blocksToBreak = blocksToBreak.subList(0, maxBlocks);
        }
        
        return blocksToBreak;
    }
    
    /**
     * Thu thập tất cả các khối gỗ liên kết từ khối ban đầu
     * @param startBlock Khối gỗ ban đầu
     * @param blocksToBreak Danh sách các khối sẽ chặt
     * @param maxHeight Giới hạn chiều cao
     */
    private static void collectWoodBlocks(Block startBlock, List<Block> blocksToBreak, int maxHeight) {
        // Sử dụng flood-fill algorithm để thu thập tất cả các khối gỗ liên kết
        List<Block> queue = new ArrayList<>();
        queue.add(startBlock);
        
        while (!queue.isEmpty() && blocksToBreak.size() < maxHeight) {
            Block current = queue.remove(0);
            
            // Kiểm tra các khối xung quanh
            for (int x = -1; x <= 1; x++) {
                for (int y = 0; y <= 1; y++) {  // Chỉ kiểm tra phía trên và cùng mức
                    for (int z = -1; z <= 1; z++) {
                        // Bỏ qua khối hiện tại
                        if (x == 0 && y == 0 && z == 0) continue;
                        
                        Block relative = current.getRelative(x, y, z);
                        
                        // Nếu là khối gỗ và chưa có trong danh sách
                        if (isWood(relative) && !blocksToBreak.contains(relative)) {
                            blocksToBreak.add(relative);
                            queue.add(relative);
                        }
                        
                        // Nếu là lá cây và có thu thập lá cây
                        if (isLeaves(relative) && !blocksToBreak.contains(relative)) {
                            blocksToBreak.add(relative);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Xử lý phù phép Leaf Collector (thu thập lá cây)
     * @param player Người chơi sử dụng rìu
     * @param block Block gỗ ban đầu
     * @param level Cấp độ phù phép
     * @return Danh sách các block lá cây sẽ được thu thập
     */
    public static List<Block> handleLeafCollector(Player player, Block block, int level) {
        // Kiểm tra cooldown để tránh lag
        if (!checkCooldown(player, LEAF_COLLECTOR)) {
            return new ArrayList<>();
        }
        
        // Bán kính thu thập lá dựa trên cấp độ
        int radius = level * 3; // Level 1: 3, Level 2: 6, Level 3: 9
        
        // Thu thập tất cả các block lá cây trong phạm vi
        List<Block> leavesToBreak = new ArrayList<>();
        
        Location center = block.getLocation();
        
        // Quét khối xung quanh, ưu tiên lá cây gần trước
        for (int r = 1; r <= radius; r++) {
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        // Chỉ xét các khối ở lớp ngoài cùng của đại lượng r
                        if (Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z))) != r) {
                            continue;
                        }
                        
                        Block relativeBlock = center.clone().add(x, y, z).getBlock();
                        
                        // Chỉ thu thập lá cây
                        if (isLeaves(relativeBlock)) {
                            leavesToBreak.add(relativeBlock);
                        }
                    }
                }
            }
            
            // Giới hạn số lượng block để tránh lag
            int maxBlocks = File.getConfig().getInt("axe_enchant.leaf_collector.max_blocks_per_break", 64);
            if (leavesToBreak.size() >= maxBlocks) {
                leavesToBreak = leavesToBreak.subList(0, maxBlocks);
                break;
            }
        }
        
        if (Storage.getStorage().isDebug() && !leavesToBreak.isEmpty()) {
            Storage.getStorage().getLogger().info("Leaf Collector: Thu thập " + leavesToBreak.size() + " lá cây với cấp độ " + level);
        }
        
        return leavesToBreak;
    }
    
    /**
     * Xử lý phù phép Regrowth (tự động tái sinh cây)
     * @param player Người chơi sử dụng rìu
     * @param block Block gỗ đã chặt
     * @param level Cấp độ phù phép
     * @return Có tái sinh cây thành công hay không
     */
    public static boolean handleRegrowth(Player player, Block block, int level) {
        if (!Storage.getStorage().getConfig().getBoolean("axe_enchant.regrowth.enabled", true)) {
            return false;
        }

        // Tăng tỷ lệ tái sinh theo cấp độ
        double chance = level * 0.25; // 25% cho cấp 1, 50% cho cấp 2, 75% cho cấp 3
        
        if (Math.random() > chance) {
            return false;
        }
        
        // Đảm bảo loại cây phù hợp cho tái sinh
        TreeType treeType = getTreeType(block.getType().name());
        if (treeType == null) {
            Storage.getStorage().getLogger().info("Không thể xác định loại cây để tái sinh");
            return false;
        }
        
        // Đảm bảo khối bên dưới phù hợp với loại cây
        Block soilBlock = block.getRelative(BlockFace.DOWN);
        if (!isSuitableSoil(soilBlock, treeType)) {
            Storage.getStorage().getLogger().info("Đất không phù hợp để tái sinh cây");
            return false;
        }

        // Lên lịch sinh lại cây sau một khoảng thời gian (tránh xung đột với quá trình phá khối)
        Bukkit.getScheduler().runTaskLater(Storage.getStorage(), () -> {
            boolean success = false;
            
            // Thử sinh cây
            success = block.getWorld().generateTree(block.getLocation(), treeType);
            
            // Nếu không thành công, thử lại một lần nữa ở vị trí hơi khác
            if (!success) {
                Location adjustedLoc = block.getLocation().clone().add(0.5, 0, 0.5);
                success = block.getWorld().generateTree(adjustedLoc, treeType);
                
                // Nếu vẫn không thành công, đặt cây non
                if (!success) {
                    Material saplingMaterial = getSaplingMaterial(treeType);
                    if (saplingMaterial != null) {
                        block.setType(saplingMaterial);
                        success = true;
                    }
                }
            }
            
            // Hiệu ứng tái sinh
            if (success) {
                playRegrowthEffect(block, level);
                player.sendMessage(Chat.colorizewp("&aPhù phép tái sinh đã kích hoạt!"));
            }
        }, 5L); // Chờ 5 tick (0.25 giây)
        
        return true;
    }
    
    /**
     * Hiển thị hiệu ứng khi tái sinh cây thành công
     * @param block Khối trung tâm
     * @param level Cấp độ enchant
     */
    private static void playRegrowthEffect(Block block, int level) {
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);
        World world = block.getWorld();
        
        // Số lượng hạt tăng theo cấp độ
        int particles = level * 15;
        double radius = level * 1.5;
        
        // Hiệu ứng xanh lá
        try {
            world.playEffect(loc, Effect.valueOf("HAPPY_VILLAGER"), 0);
        } catch (Exception e) {
            // Dự phòng cho phiên bản cũ hơn, tạo hiệu ứng ngẫu nhiên
            for (int i = 0; i < particles; i++) {
                double x = loc.getX() + (Math.random() - 0.5) * radius;
                double y = loc.getY() + (Math.random() * radius);
                double z = loc.getZ() + (Math.random() - 0.5) * radius;
                world.playEffect(new Location(world, x, y, z), Effect.SMOKE, 0);
            }
        }
        
        // Âm thanh
        world.playSound(loc, Sound.BLOCK_GRASS_PLACE, 1.0f, 0.8f);
        if (level >= 2) {
            world.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        }
    }
    
    /**
     * Lấy loại cây non tương ứng với loại cây
     * @param treeType Loại cây
     * @return Material cây non tương ứng
     */
    private static Material getSaplingMaterial(TreeType treeType) {
        try {
            Material saplingMaterial = Material.OAK_SAPLING; // Mặc định
            
            switch (treeType) {
                case TREE:
                case BIG_TREE:
                    saplingMaterial = Material.OAK_SAPLING;
                    break;
                case BIRCH:
                case TALL_BIRCH:
                    saplingMaterial = Material.BIRCH_SAPLING;
                    break;
                case REDWOOD:
                case TALL_REDWOOD:
                case MEGA_REDWOOD:
                    saplingMaterial = Material.SPRUCE_SAPLING;
                    break;
                case JUNGLE:
                case SMALL_JUNGLE:
                case COCOA_TREE:
                case JUNGLE_BUSH:
                    saplingMaterial = Material.JUNGLE_SAPLING;
                    break;
                case ACACIA:
                    saplingMaterial = Material.ACACIA_SAPLING;
                    break;
                case DARK_OAK:
                    saplingMaterial = Material.DARK_OAK_SAPLING;
                    break;
                default:
                    return null;
            }
            
            return saplingMaterial;
        } catch (Exception e) {
            Storage.getStorage().getLogger().info("Không thể xác định loại cây non: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Kiểm tra xem đất có phù hợp để trồng cây không
     * @param soil Khối đất
     * @param treeType Loại cây
     * @return true nếu phù hợp
     */
    private static boolean isSuitableSoil(Block soil, TreeType treeType) {
        Material type = soil.getType();
        
        // Loại đất phổ biến cho hầu hết các loại cây
        if (type == Material.DIRT || type == Material.GRASS_BLOCK || type == Material.PODZOL) {
            return true;
        }
        
        // Loại đất đặc biệt cho một số loại cây
        if (treeType == TreeType.ACACIA || treeType == TreeType.JUNGLE || treeType == TreeType.SMALL_JUNGLE) {
            return type == Material.DIRT || type == Material.GRASS_BLOCK || type == Material.PODZOL || type == Material.RED_SAND;
        }
        
        // Cho cây nấm
        if (treeType == TreeType.BROWN_MUSHROOM || treeType == TreeType.RED_MUSHROOM) {
            return type == Material.DIRT || type == Material.GRASS_BLOCK || type == Material.PODZOL || type == Material.MYCELIUM;
        }
        
        return false;
    }
    
    /**
     * Xác định loại cây dựa trên khối gỗ
     * @param log Khối gỗ
     * @return Loại cây
     */
    private static TreeType getTreeType(Block log) {
        Material type = log.getType();
        String typeName = type.name();
        
        if (typeName.contains("OAK")) {
            return TreeType.TREE;
        } else if (typeName.contains("BIRCH")) {
            return TreeType.BIRCH;
        } else if (typeName.contains("SPRUCE")) {
            return TreeType.REDWOOD;
        } else if (typeName.contains("JUNGLE")) {
            return TreeType.JUNGLE;
        } else if (typeName.contains("ACACIA")) {
            return TreeType.ACACIA;
        } else if (typeName.contains("DARK_OAK")) {
            return TreeType.DARK_OAK;
        }
        
        // Mặc định
        return TreeType.TREE;
    }
    
    /**
     * Xử lý phù phép Auto Plant (tự động trồng cây)
     * @param player Người chơi sử dụng rìu
     * @param block Block gỗ đã chặt
     * @return Đã trồng cây thành công hay không
     */
    public static boolean handleAutoPlant(Player player, Block block) {
        // Kiểm tra cooldown để tránh lag
        if (!checkCooldown(player, AUTO_PLANT)) {
            return false;
        }
        
        // Tìm block đất bên dưới
        Block soilBlock = block.getRelative(BlockFace.DOWN);
        if (!isSoil(soilBlock)) {
            return false;
        }
        
        // Xác định loại cây con dựa vào loại gỗ
        Material saplingType = getSaplingType(block.getType().name());
        if (saplingType == null) {
            return false;
        }
        
        // Kiểm tra trong túi đồ của người chơi
        boolean hasSapling = false;
        int saplingSlot = -1;
        
        // Tìm kiếm trong túi đồ của người chơi
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == saplingType && item.getAmount() > 0) {
                hasSapling = true;
                saplingSlot = i;
                break;
            }
        }
        
        // Kiểm tra xem có cây con trong kho không (cho khả năng tương thích)
        int saplingInStorage = 0;
        String saplingKey = saplingType.name();
        if (MineManager.hasPlayerBlock(player, saplingKey)) {
            saplingInStorage = MineManager.getPlayerBlock(player, saplingKey);
        }
        
        if (hasSapling) {
            // Sử dụng cây con từ túi đồ
            ItemStack saplingItem = player.getInventory().getItem(saplingSlot);
            saplingItem.setAmount(saplingItem.getAmount() - 1);
            player.getInventory().setItem(saplingSlot, saplingItem.getAmount() <= 0 ? null : saplingItem);
            
            // Đặt cây con
            block.setType(Material.AIR);
            block.setType(saplingType);
            
            if (Storage.getStorage().isDebug()) {
                Storage.getStorage().getLogger().info("Auto Plant: Đã sử dụng 1 " + saplingType.name() + " từ túi đồ của " + player.getName());
            }
            
            return true;
        } else if (saplingInStorage > 0) {
            // Sử dụng cây con từ kho (phương pháp cũ)
            block.setType(Material.AIR);
            block.setType(saplingType);
            
            // Giảm số lượng trong kho
            MineManager.removeBlockAmount(player, saplingKey, 1);
            
            if (Storage.getStorage().isDebug()) {
                Storage.getStorage().getLogger().info("Auto Plant: Đã sử dụng 1 " + saplingType.name() + " từ kho của " + player.getName());
            }
            
            return true;
        } else {
            // Không có cây con trong túi đồ hoặc kho
            return false;
        }
    }
    
    /**
     * Lấy loại cây con tương ứng với loại gỗ
     * @param woodType Tên của loại gỗ
     * @return Material cây con tương ứng
     */
    private static Material getSaplingType(String woodType) {
        woodType = woodType.toUpperCase();
        
        try {
            if (woodType.contains("OAK") && !woodType.contains("DARK_OAK")) return Material.valueOf("OAK_SAPLING");
            if (woodType.contains("SPRUCE")) return Material.valueOf("SPRUCE_SAPLING");
            if (woodType.contains("BIRCH")) return Material.valueOf("BIRCH_SAPLING");
            if (woodType.contains("JUNGLE")) return Material.valueOf("JUNGLE_SAPLING");
            if (woodType.contains("ACACIA")) return Material.valueOf("ACACIA_SAPLING");
            if (woodType.contains("DARK_OAK")) return Material.valueOf("DARK_OAK_SAPLING");
            if (woodType.contains("CRIMSON")) return Material.valueOf("CRIMSON_FUNGUS");
            if (woodType.contains("WARPED")) return Material.valueOf("WARPED_FUNGUS");
            
            // Cho các phiên bản cũ
            if (woodType.contains("LOG") || woodType.contains("WOOD")) {
                if (Bukkit.getVersion().contains("1.8") || Bukkit.getVersion().contains("1.9") || 
                    Bukkit.getVersion().contains("1.10") || Bukkit.getVersion().contains("1.11") || 
                    Bukkit.getVersion().contains("1.12")) {
                    return Material.valueOf("SAPLING");
                } else {
                    return Material.valueOf("OAK_SAPLING");
                }
            }
        } catch (IllegalArgumentException e) {
            // Fallback cho phiên bản cũ
            try {
                return Material.valueOf("SAPLING");
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        
        return null;
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