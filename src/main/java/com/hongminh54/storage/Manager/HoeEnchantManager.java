package com.hongminh54.storage.Manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.Chat;
import com.hongminh54.storage.Utils.File;

/**
 * Manager quản lý các phù phép cho cuốc
 */
public class HoeEnchantManager {
    
    // Danh sách phù phép hỗ trợ
    public static final String FARMERS_TOUCH = "farmers_touch";
    public static final String FERTILE_SOIL = "fertile_soil";
    public static final String FARMERS_WISDOM = "farmers_wisdom";
    public static final String REGENERATION = "regeneration";
    
    // Cooldown để tránh lag
    private static final Map<UUID, Map<String, Long>> cooldownMap = new HashMap<>();
    private static final int DEFAULT_COOLDOWN = 1000; // 1 giây để tránh lag
    private static final Random random = new Random();
    
    /**
     * Kiểm tra xem một item có phải là cuốc hay không
     * @param item ItemStack cần kiểm tra
     * @return true nếu là cuốc, false nếu không phải
     */
    public static boolean isHoe(ItemStack item) {
        if (item == null) return false;
        
        String typeName = item.getType().name();
        
        // Kiểm tra trực tiếp tất cả các loại cuốc có thể có
        return typeName.equals("WOOD_HOE") || 
               typeName.equals("STONE_HOE") || 
               typeName.equals("IRON_HOE") || 
               typeName.equals("GOLD_HOE") || 
               typeName.equals("DIAMOND_HOE") ||
               typeName.equals("WOODEN_HOE") ||  // 1.13+
               typeName.equals("GOLDEN_HOE") ||  // 1.13+
               typeName.equals("NETHERITE_HOE") || // 1.16+
               typeName.endsWith("_HOE"); // Để tương thích với các phiên bản khác
    }
    
    /**
     * Kiểm tra xem một block có phải là cây trồng không
     * @param block Block cần kiểm tra
     * @return true nếu là cây trồng, false nếu không phải
     */
    public static boolean isCrop(Block block) {
        if (block == null) return false;
        
        String typeName = block.getType().name();
        
        return typeName.equals("CROPS") || 
               typeName.equals("WHEAT") || 
               typeName.equals("CARROT") || 
               typeName.equals("POTATOES") ||
               typeName.equals("POTATO") ||
               typeName.equals("CARROTS") ||
               typeName.equals("BEETROOT_BLOCK") ||
               typeName.equals("BEETROOTS") ||
               typeName.equals("NETHER_WART") ||
               typeName.equals("PUMPKIN_STEM") ||
               typeName.equals("MELON_STEM") ||
               typeName.equals("COCOA");
    }
    
    /**
     * Kiểm tra xem một block có phải là đất màu mỡ không
     * @param block Block cần kiểm tra
     * @return true nếu là đất màu mỡ, false nếu không phải
     */
    public static boolean isSoil(Block block) {
        if (block == null) return false;
        
        String typeName = block.getType().name();
        
        return typeName.equals("SOIL") || 
               typeName.equals("FARMLAND");
    }
    
    /**
     * Lấy cấp độ phù phép của một loại phù phép cụ thể
     * @param item ItemStack cần kiểm tra
     * @param enchantType Loại phù phép
     * @return Cấp độ phù phép (0 nếu không có)
     */
    public static int getEnchantLevel(ItemStack item, String enchantType) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return 0;
        }
        
        List<String> lore = item.getItemMeta().getLore();
        if (lore == null) return 0;
        
        String configPath = "hoe_enchant." + enchantType + ".lore_prefix";
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
            case FARMERS_TOUCH: return "Nông Dân Chuyên Nghiệp";
            case FERTILE_SOIL: return "Đất Màu Mỡ";
            case FARMERS_WISDOM: return "Kinh Nghiệm Nông Dân";
            case REGENERATION: return "Tái Sinh";
            default: return enchantType;
        }
    }
    
    /**
     * Thêm phù phép vào cuốc
     * @param item Cuốc cần thêm phù phép
     * @param enchantType Loại phù phép
     * @param level Cấp độ phù phép (1-3)
     * @return Item đã được thêm phù phép
     */
    public static ItemStack addHoeEnchant(ItemStack item, String enchantType, int level) {
        if (!isHoe(item)) {
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
        String configPath = "hoe_enchant." + enchantType + ".lore_prefix";
        String defaultPrefix = "&a&l" + getDefaultPrefix(enchantType);
        String enchantPrefix = Chat.colorizewp(File.getConfig().getString(configPath, defaultPrefix));
        
        lore.removeIf(line -> line.contains(enchantPrefix));
        
        // Thêm phù phép mới
        String enchantText = Chat.colorizewp(enchantPrefix + " " + getRomanNumeral(level));
        lore.add(enchantText);
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        if (Storage.getStorage().isDebug()) {
            Storage.getStorage().getLogger().info("Đã thêm phù phép " + enchantType + " " + level + " vào cuốc: " + item.getType().name());
        }
        
        return item;
    }
    
    /**
     * Xóa phù phép khỏi item
     * @param item Item cần xóa phù phép
     * @param enchantType Loại phù phép cần xóa
     * @return Item sau khi đã xóa phù phép
     */
    public static ItemStack removeHoeEnchant(ItemStack item, String enchantType) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return item;
        }
        
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.getLore();
        
        // Xóa phù phép cụ thể
        String configPath = "hoe_enchant." + enchantType + ".lore_prefix";
        String defaultPrefix = "&a&l" + getDefaultPrefix(enchantType);
        String enchantPrefix = Chat.colorizewp(File.getConfig().getString(configPath, defaultPrefix));
        
        lore.removeIf(line -> line.contains(enchantPrefix));
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Kiểm tra cooldown của phù phép
     * @param player Người chơi
     * @param enchantType Loại phù phép
     * @return true nếu hết cooldown, false nếu vẫn trong thời gian cooldown
     */
    public static boolean checkCooldown(Player player, String enchantType) {
        UUID uuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // Khởi tạo map nếu chưa có
        if (!cooldownMap.containsKey(uuid)) {
            cooldownMap.put(uuid, new HashMap<>());
        }
        
        Map<String, Long> playerCooldowns = cooldownMap.get(uuid);
        
        // Kiểm tra cooldown
        if (playerCooldowns.containsKey(enchantType)) {
            long lastUse = playerCooldowns.get(enchantType);
            int cooldownTime = File.getConfig().getInt("hoe_enchant." + enchantType + ".cooldown", DEFAULT_COOLDOWN);
            
            if (currentTime - lastUse < cooldownTime) {
                return false; // Vẫn trong thời gian cooldown
            }
        }
        
        // Cập nhật thời gian sử dụng
        playerCooldowns.put(enchantType, currentTime);
        return true;
    }
    
    /**
     * Xử lý phù phép Nông Dân Chuyên Nghiệp (Farmers Touch)
     * @param player Người chơi
     * @param block Block cây trồng bị phá
     * @param level Cấp độ phù phép
     * @return true nếu thành công, false nếu thất bại
     */
    public static boolean handleFarmersTouch(Player player, Block block, int level) {
        // Kiểm tra cooldown
        if (!checkCooldown(player, FARMERS_TOUCH)) {
            return false;
        }
        
        // Kiểm tra xem có phải cây trồng đã trưởng thành chưa
        if (!isMatureCrop(block)) {
            return false;
        }
        
        // Lấy thông tin về cây trồng
        Material cropType = block.getType();
        Block soil = block.getRelative(BlockFace.DOWN);
        
        // Phải có đất bên dưới
        if (!isSoil(soil)) {
            return false;
        }
        
        // Tự động trồng lại cây trồng sau khi thu hoạch
        new BukkitRunnable() {
            @Override
            public void run() {
                Material seedType = getSeedType(cropType);
                if (seedType != null) {
                    block.setType(seedType);
                }
            }
        }.runTaskLater(Storage.getStorage(), 2L); // Delay 2 tick để đảm bảo block đã được phá
        
        // Tính toán số lượng vật phẩm bổ sung dựa trên cấp độ
        int bonusDropChance = 15 * level; // 15/30/45% cơ hội nhận thêm vật phẩm
        
        // Xử lý vật phẩm drop ra thêm (được xử lý tại listener)
        if (random.nextInt(100) < bonusDropChance) {
            // Logic để thêm vật phẩm sẽ được xử lý tại Listener
            return true;
        }
        
        return true;
    }
    
    /**
     * Kiểm tra xem cây trồng đã trưởng thành chưa
     * @param block Block cần kiểm tra
     * @return true nếu đã trưởng thành, false nếu chưa
     */
    private static boolean isMatureCrop(Block block) {
        if (!isCrop(block)) return false;
        
        String typeName = block.getType().name();
        byte data = block.getData();
        
        // Kiểm tra từng loại cây trồng
        if (typeName.equals("CROPS") || typeName.equals("WHEAT")) {
            return data >= 7; // Lúa mì trưởng thành khi data = 7
        } else if (typeName.equals("CARROTS") || typeName.equals("CARROT")) {
            return data >= 7; // Cà rốt trưởng thành khi data = 7
        } else if (typeName.equals("POTATOES") || typeName.equals("POTATO")) {
            return data >= 7; // Khoai tây trưởng thành khi data = 7
        } else if (typeName.equals("BEETROOTS") || typeName.equals("BEETROOT_BLOCK")) {
            return data >= 3; // Củ cải đường trưởng thành khi data = 3
        } else if (typeName.equals("NETHER_WART")) {
            return data >= 3; // Nether wart trưởng thành khi data = 3
        } else if (typeName.equals("COCOA")) {
            return (data & 0x8) == 0x8; // Cocoa trưởng thành khi bit thứ 4 được set
        } else if (typeName.equals("PUMPKIN_STEM") || typeName.equals("MELON_STEM")) {
            return data >= 7; // Thân bí ngô/dưa hấu trưởng thành khi data = 7
        }
        
        return false;
    }
    
    /**
     * Lấy loại hạt giống tương ứng với cây trồng
     * @param cropType Loại cây trồng
     * @return Material của hạt giống
     */
    private static Material getSeedType(Material cropType) {
        String typeName = cropType.name();
        
        if (typeName.equals("CROPS") || typeName.equals("WHEAT")) {
            try {
                return Material.valueOf("SEEDS");
            } catch (IllegalArgumentException e) {
                return Material.valueOf("WHEAT_SEEDS");
            }
        } else if (typeName.equals("CARROTS") || typeName.equals("CARROT")) {
            return cropType; // Carrot cây trồng và hạt giống giống nhau
        } else if (typeName.equals("POTATOES") || typeName.equals("POTATO")) {
            return cropType; // Potato cây trồng và hạt giống giống nhau
        } else if (typeName.equals("BEETROOTS") || typeName.equals("BEETROOT_BLOCK")) {
            try {
                return Material.valueOf("BEETROOT_SEEDS");
            } catch (IllegalArgumentException e) {
                return Material.valueOf("BEETROOT_BLOCK"); // Fallback cho phiên bản cũ
            }
        } else if (typeName.equals("NETHER_WART")) {
            return Material.valueOf("NETHER_WART");
        } else if (typeName.equals("COCOA")) {
            try {
                return Material.valueOf("COCOA_BEANS");
            } catch (IllegalArgumentException e) {
                try {
                    return Material.valueOf("INK_SACK"); // Trong 1.12.2, cocoa là data 3 của INK_SACK
                } catch (IllegalArgumentException e2) {
                    return null;
                }
            }
        } else if (typeName.equals("PUMPKIN_STEM")) {
            try {
                return Material.valueOf("PUMPKIN_SEEDS");
            } catch (IllegalArgumentException e) {
                return null;
            }
        } else if (typeName.equals("MELON_STEM")) {
            try {
                return Material.valueOf("MELON_SEEDS");
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        
        return null;
    }
    
    /**
     * Xử lý phù phép Đất Màu Mỡ (Fertile Soil)
     * @param player Người chơi
     * @param block Block cây trồng bị phá
     * @param level Cấp độ phù phép
     * @return true nếu thành công, false nếu thất bại
     */
    public static boolean handleFertileSoil(Player player, Block block, int level) {
        // Kiểm tra cooldown
        if (!checkCooldown(player, FERTILE_SOIL)) {
            return false;
        }
        
        // Lấy block đất bên dưới
        Block soil = block.getRelative(BlockFace.DOWN);
        
        // Kiểm tra xem có phải đất có thể trồng trọt không
        if (!isSoil(soil) && !soil.getType().name().equals("DIRT")) {
            return false;
        }
        
        // Cơ hội biến đất thường thành đất màu mỡ
        int transformChance = 20 * level; // 20/40/60% tùy cấp độ
        
        if (soil.getType().name().equals("DIRT") && random.nextInt(100) < transformChance) {
            // Chuyển đất thường thành farmland (đất canh tác)
            try {
                soil.setType(Material.valueOf("FARMLAND"));
            } catch (IllegalArgumentException e) {
                soil.setType(Material.valueOf("SOIL"));
            }
            return true;
        }
        
        // Tăng tốc độ phát triển các cây trồng xung quanh
        if (random.nextInt(100) < (30 * level)) { // 30/60/90% cơ hội tùy cấp độ
            // Tìm các cây trồng xung quanh trong bán kính 3x3
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && z == 0) continue; // Bỏ qua block hiện tại
                    
                    Block nearbyBlock = block.getRelative(x, 0, z);
                    if (isCrop(nearbyBlock) && !isMatureCrop(nearbyBlock)) {
                        // Tăng giai đoạn phát triển của cây
                        byte currentStage = nearbyBlock.getData();
                        byte maxStage = getCropMaxStage(nearbyBlock.getType());
                        
                        if (currentStage < maxStage) {
                            try {
                                // Sử dụng reflection để gọi phương thức setData an toàn cho nhiều phiên bản
                                try {
                                    // Sử dụng reflection để gọi phương thức setData
                                    java.lang.reflect.Method setDataMethod = Block.class.getMethod("setData", byte.class);
                                    setDataMethod.invoke(nearbyBlock, (byte) Math.min(currentStage + level, maxStage));
                                } catch (NoSuchMethodException e) {
                                    try {
                                        // Thử với phiên bản có tham số applyPhysics
                                        java.lang.reflect.Method setDataMethod = Block.class.getMethod("setData", byte.class, boolean.class);
                                        setDataMethod.invoke(nearbyBlock, (byte) Math.min(currentStage + level, maxStage), true);
                                    } catch (NoSuchMethodException ex) {
                                        // Không tìm thấy phương thức, có thể là phiên bản mới hơn
                                        Storage.getStorage().getLogger().warning("Không tìm thấy phương thức setData");
                                    }
                                }
                            } catch (Exception e) {
                                // Xử lý các ngoại lệ khác (IllegalAccessException, InvocationTargetException)
                                Storage.getStorage().getLogger().warning("Không thể cập nhật stage cho cây trồng: " + e.getMessage());
                            }
                        }
                    }
                }
            }
            return true;
        }
        
        return false;
    }
    
    /**
     * Lấy giai đoạn phát triển tối đa của cây trồng
     * @param cropType Loại cây trồng
     * @return Giá trị data của giai đoạn phát triển tối đa
     */
    private static byte getCropMaxStage(Material cropType) {
        String typeName = cropType.name();
        
        if (typeName.equals("CROPS") || typeName.equals("WHEAT") ||
            typeName.equals("CARROTS") || typeName.equals("CARROT") ||
            typeName.equals("POTATOES") || typeName.equals("POTATO") ||
            typeName.equals("PUMPKIN_STEM") || typeName.equals("MELON_STEM")) {
            return 7;
        } else if (typeName.equals("BEETROOTS") || typeName.equals("BEETROOT_BLOCK") ||
                  typeName.equals("NETHER_WART")) {
            return 3;
        } else if (typeName.equals("COCOA")) {
            return 8; // Cocoa có bit thứ 4 được set khi trưởng thành
        }
        
        return 7; // Mặc định cho các loại khác
    }
    
    /**
     * Chuyển đổi số thành chữ số La Mã
     * @param number Số cần chuyển đổi
     * @return Chuỗi chữ số La Mã
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