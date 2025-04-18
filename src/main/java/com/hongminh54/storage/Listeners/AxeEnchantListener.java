package com.hongminh54.storage.Listeners;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.jetbrains.annotations.NotNull;

import com.hongminh54.storage.Manager.AxeEnchantManager;
import com.hongminh54.storage.Manager.MineManager;
import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.Chat;
import com.hongminh54.storage.Utils.File;
import com.hongminh54.storage.WorldGuard.WorldGuard;

/**
 * Lớp xử lý sự kiện phá block với rìu có phù phép
 */
public class AxeEnchantListener implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockBreak(@NotNull BlockBreakEvent e) {
        Player player = e.getPlayer();
        Block block = e.getBlock();
        ItemStack handItem = player.getInventory().getItemInMainHand();
        
        // Kiểm tra điều kiện cơ bản
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        
        if (handItem == null || handItem.getType() == Material.AIR) {
            return;
        }
        
        if (!AxeEnchantManager.isAxe(handItem)) {
            return;
        }
        
        // Kiểm tra xem block có phải là gỗ không
        if (!AxeEnchantManager.isWood(block)) {
            return;
        }
        
        // Kiểm tra các điều kiện khác
        if (Storage.isWorldGuardInstalled() && !WorldGuard.handleForLocation(player, block.getLocation())) {
            return;
        }
        
        if (File.getConfig().getBoolean("prevent_rebreak") && isPlacedBlock(block)) {
            return;
        }
        
        if (File.getConfig().contains("blacklist_world") && 
            File.getConfig().getStringList("blacklist_world").contains(player.getWorld().getName())) {
            return;
        }
        
        // Đây là block gỗ, kiểm tra từng phù phép
        boolean debug = Storage.getStorage().isDebug();
        
        // Kiểm tra và xử lý phù phép Tree Cutter (Chặt Cây)
        int treeCutterLevel = AxeEnchantManager.getEnchantLevel(handItem, AxeEnchantManager.TREE_CUTTER);
        if (treeCutterLevel > 0) {
            if (debug) {
                Storage.getStorage().getLogger().info("AxeEnchant: Bắt đầu xử lý phù phép Chặt Cây với cấp độ " + treeCutterLevel);
            }
            
            List<Block> blocksToBreak = AxeEnchantManager.handleTreeCutter(player, block, treeCutterLevel);
            
            // Nếu chỉ có 1 block (block chính) thì để hệ thống khác xử lý
            if (blocksToBreak.size() <= 1) {
                // Kiểm tra các phù phép khác
                processOtherEnchants(player, block, handItem);
                return;
            }
            
            if (debug) {
                Storage.getStorage().getLogger().info("Tree Cutter: Xử lý " + blocksToBreak.size() + " block");
            }
            
            // Hủy sự kiện ban đầu vì sẽ được xử lý thủ công
            e.setCancelled(true);
            
            // Xử lý khối theo cơ chế auto pickup
            handleBlockBreak(player, blocksToBreak);
            
            // Hiệu ứng âm thanh và hạt
            playEffects(player, block.getLocation(), "tree_cutter");
            
            // Thông báo hiệu ứng phá khối theo cấp độ
            sendEffectMessage(player, treeCutterLevel, blocksToBreak.size(), "tree_cutter");
            
            // Kiểm tra và xử lý phù phép Regrowth sau khi phá cây
            processRegrowth(player, block, handItem);
            
            // Kiểm tra và xử lý phù phép Auto Plant sau khi phá cây
            processAutoPlant(player, block, handItem);
            
            return;
        }
        
        // Kiểm tra phù phép Leaf Collector (Thu Lá)
        int leafCollectorLevel = AxeEnchantManager.getEnchantLevel(handItem, AxeEnchantManager.LEAF_COLLECTOR);
        if (leafCollectorLevel > 0) {
            // Thu thập lá cây xung quanh
            List<Block> leavesToBreak = AxeEnchantManager.handleLeafCollector(player, block, leafCollectorLevel);
            
            if (!leavesToBreak.isEmpty()) {
                if (debug) {
                    Storage.getStorage().getLogger().info("Leaf Collector: Thu thập " + leavesToBreak.size() + " lá cây");
                }
                
                // Xử lý phá lá cây
                handleBlockBreak(player, leavesToBreak);
                
                // Hiệu ứng khi thu thập lá
                playEffects(player, block.getLocation(), "leaf_collector");
                
                // Thông báo hiệu ứng thu thập lá
                sendEffectMessage(player, leafCollectorLevel, leavesToBreak.size(), "leaf_collector");
            }
        }
        
        // Xử lý các phù phép khác
        processOtherEnchants(player, block, handItem);
    }
    
    /**
     * Xử lý các phù phép khác ngoài Tree Cutter
     */
    private void processOtherEnchants(Player player, Block block, ItemStack handItem) {
        // Xử lý phù phép Regrowth
        processRegrowth(player, block, handItem);
        
        // Xử lý phù phép Auto Plant
        processAutoPlant(player, block, handItem);
    }
    
    /**
     * Xử lý phù phép Regrowth (Tái Sinh)
     */
    private void processRegrowth(Player player, Block block, ItemStack handItem) {
        int regrowthLevel = AxeEnchantManager.getEnchantLevel(handItem, AxeEnchantManager.REGROWTH);
        if (regrowthLevel > 0) {
            boolean success = AxeEnchantManager.handleRegrowth(player, block, regrowthLevel);
            
            if (success) {
                // Hiệu ứng khi tái sinh cây
                playEffects(player, block.getLocation(), "regrowth");
                
                // Thông báo hiệu ứng tái sinh cây
                String message = File.getConfig().getString("axe_enchant.regrowth.effect_message.format", 
                                    "&aTự động tái sinh cây thành công!");
                
                if (message != null && !message.isEmpty()) {
                    player.sendMessage(Chat.colorizewp(message));
                }
            }
        }
    }
    
    /**
     * Xử lý phù phép Auto Plant (Tự Trồng)
     */
    private void processAutoPlant(Player player, Block block, ItemStack handItem) {
        int autoPlantLevel = AxeEnchantManager.getEnchantLevel(handItem, AxeEnchantManager.AUTO_PLANT);
        if (autoPlantLevel > 0) {
            boolean success = AxeEnchantManager.handleAutoPlant(player, block);
            
            if (success) {
                // Hiệu ứng khi tự động trồng cây
                playEffects(player, block.getLocation(), "auto_plant");
                
                // Thông báo khi tự động trồng cây
                String message = File.getConfig().getString("axe_enchant.auto_plant.effect_message.format", 
                                    "&aTự động trồng cây con thành công!");
                
                if (message != null && !message.isEmpty()) {
                    player.sendMessage(Chat.colorizewp(message));
                }
            }
        }
    }
    
    /**
     * Phát hiệu ứng âm thanh và hạt khi kích hoạt phù phép
     * 
     * @param player Người chơi sử dụng phù phép
     * @param location Vị trí khối bị phá
     * @param enchantType Loại phù phép
     */
    private void playEffects(Player player, Location location, String enchantType) {
        // Xử lý âm thanh
        String soundConfig = File.getConfig().getString("axe_enchant." + enchantType + ".sound", "ENTITY_EXPERIENCE_ORB_PICKUP:0.3:1.2");
        if (soundConfig != null && !soundConfig.isEmpty()) {
            try {
                String[] soundParts = soundConfig.split(":");
                String soundName = soundParts[0];
                float volume = soundParts.length > 1 ? Float.parseFloat(soundParts[1]) : 0.3f;
                float pitch = soundParts.length > 2 ? Float.parseFloat(soundParts[2]) : 1.2f;
                
                try {
                    // Thử chuẩn hóa tên sound
                    Sound sound = Sound.valueOf(soundName);
                    player.playSound(location, sound, volume, pitch);
                } catch (IllegalArgumentException e) {
                    // Fallback cho các phiên bản khác
                    player.playSound(location, soundName, volume, pitch);
                }
            } catch (Exception e) {
                Storage.getStorage().getLogger().warning("Lỗi khi phát âm thanh " + enchantType + ": " + e.getMessage());
            }
        }
        
        // Xử lý hiệu ứng hạt
        String particleConfig = File.getConfig().getString("axe_enchant." + enchantType + ".particle", "VILLAGER_HAPPY:0.3:0.3:0.3:0.05:5");
        if (particleConfig != null && !particleConfig.isEmpty()) {
            try {
                String[] particleParts = particleConfig.split(":");
                String particleName = particleParts[0];
                double offsetX = particleParts.length > 1 ? Double.parseDouble(particleParts[1]) : 0.3;
                double offsetY = particleParts.length > 2 ? Double.parseDouble(particleParts[2]) : 0.3;
                double offsetZ = particleParts.length > 3 ? Double.parseDouble(particleParts[3]) : 0.3;
                double speed = particleParts.length > 4 ? Double.parseDouble(particleParts[4]) : 0.05;
                int count = particleParts.length > 5 ? Integer.parseInt(particleParts[5]) : 5;
                
                // Tạo hiệu ứng phù hợp với phiên bản Minecraft
                try {
                    // Kiểm tra xem có hỗ trợ enum Particle không
                    if (isParticleEnumSupported()) {
                        Particle particle = Particle.valueOf(particleName);
                        player.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed);
                    } else {
                        // Fallback cho phiên bản cũ
                        for (int i = 0; i < count; i++) {
                            double x = location.getX() + (Math.random() * 2 - 1) * offsetX;
                            double y = location.getY() + (Math.random() * 2 - 1) * offsetY;
                            double z = location.getZ() + (Math.random() * 2 - 1) * offsetZ;
                            location.getWorld().playEffect(new Location(location.getWorld(), x, y, z), 
                                                         org.bukkit.Effect.valueOf(convertParticleName(particleName)), 0);
                        }
                    }
                } catch (Exception e) {
                    Storage.getStorage().getLogger().warning("Lỗi khi tạo hiệu ứng hạt " + enchantType + ": " + e.getMessage());
                }
            } catch (Exception e) {
                Storage.getStorage().getLogger().warning("Lỗi khi xử lý cấu hình hiệu ứng hạt " + enchantType + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Kiểm tra xem enum Particle có được hỗ trợ không
     */
    private boolean isParticleEnumSupported() {
        try {
            Class.forName("org.bukkit.Particle");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Chuyển đổi tên particle để tương thích với các phiên bản cũ
     */
    private String convertParticleName(String particleName) {
        switch (particleName) {
            case "VILLAGER_HAPPY": return "HAPPY_VILLAGER";
            case "COMPOSTER": return "SPELL";
            case "SPELL_WITCH": return "WITCH_MAGIC";
            case "TOTEM": return "SPELL_MOB";
            default: return particleName;
        }
    }
    
    /**
     * Gửi thông báo hiệu ứng khi sử dụng phù phép
     * 
     * @param player Người chơi nhận thông báo
     * @param level Cấp độ phù phép
     * @param blockCount Số lượng block đã phá
     * @param enchantType Loại phù phép
     */
    private void sendEffectMessage(Player player, int level, int blockCount, String enchantType) {
        if (!File.getConfig().getBoolean("axe_enchant." + enchantType + ".effect_message.enabled", true)) {
            return;
        }
        
        String format = File.getConfig().getString("axe_enchant." + enchantType + ".effect_message.format", 
                                                 "&a✓ &ePhù phép %name% %level% &aphá &f%blocks% &ablocks!");
        
        if (format != null && !format.isEmpty()) {
            String enchantName = "";
            switch (enchantType) {
                case AxeEnchantManager.TREE_CUTTER: enchantName = "Chặt Cây"; break;
                case AxeEnchantManager.LEAF_COLLECTOR: enchantName = "Thu Lá"; break;
                case AxeEnchantManager.REGROWTH: enchantName = "Tái Sinh"; break;
                case AxeEnchantManager.AUTO_PLANT: enchantName = "Tự Trồng"; break;
            }
            
            String message = format.replace("%level%", getRomanLevel(level))
                                 .replace("%blocks%", String.valueOf(blockCount))
                                 .replace("%name%", enchantName);
            
            player.sendMessage(Chat.colorizewp(message));
        }
    }
    
    /**
     * Chuyển đổi cấp độ số thành chữ số La Mã
     */
    private String getRomanLevel(int level) {
        switch (level) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            default: return String.valueOf(level);
        }
    }
    
    /**
     * Xử lý việc phá nhiều khối cùng một lúc
     * @param player Người chơi
     * @param blocks Danh sách các block cần phá
     */
    private void handleBlockBreak(Player player, List<Block> blocks) {
        boolean debug = Storage.getStorage().isDebug();
        
        // Kiểm tra xem danh sách có chứa khối gỗ hoặc lá cây không
        boolean containsWoodOrLeaves = false;
        for (Block block : blocks) {
            String blockType = block.getType().name().toUpperCase();
            if (blockType.contains("LOG") || blockType.contains("WOOD") || 
                blockType.endsWith("_STEM") || blockType.contains("STRIPPED") ||
                blockType.contains("LEAVES") || blockType.equals("LEAVES") || 
                blockType.equals("LEAVES_2")) {
                containsWoodOrLeaves = true;
                break;
            }
        }
        
        // Nếu danh sách chứa gỗ hoặc lá cây, sử dụng breakNaturally để rơi ra vật phẩm
        if (containsWoodOrLeaves) {
            if (debug) Storage.getStorage().getLogger().info("Axe Enchant: Xử lý block gỗ/lá cây bằng cách rơi ra vật phẩm");
            
            for (Block block : blocks) {
                block.breakNaturally(player.getInventory().getItemInMainHand());
            }
            return;
        }
        
        // Xử lý hàng loạt các khối không phải gỗ/lá cây
        if (MineManager.isAutoPickup(player)) {
            if (debug) Storage.getStorage().getLogger().info("Axe Enchant: Xử lý batch với auto pickup");
            
            Map<String, Integer> blockResults = MineManager.processBlocksBatchFromList(player, blocks);
            
            // Phá hủy các block đã xử lý (không drop items)
            for (Block block : blocks) {
                block.setType(Material.AIR);
            }
            
            // Thông báo kết quả
            if (!blockResults.isEmpty() && File.getConfig().getBoolean("axe_enchant.show_batch_message", true)) {
                StringBuilder message = new StringBuilder(Objects.requireNonNull(
                    File.getConfig().getString("axe_enchant.batch_message", "&aBạn đã thu hoạch được:"))
                );
                
                for (Map.Entry<String, Integer> entry : blockResults.entrySet()) {
                    String materialName = MineManager.getMaterialDisplayName(entry.getKey());
                    message.append("\n&7- &e").append(materialName).append(": &a+").append(entry.getValue());
                }
                
                player.sendMessage(Chat.colorizewp(message.toString()));
            }
        } else {
            if (debug) Storage.getStorage().getLogger().info("Axe Enchant: Xử lý block thông thường (không auto pickup)");
            
            // Cho phép các block bình thường rơi ra nếu không bật auto pickup
            for (Block block : blocks) {
                block.breakNaturally(player.getInventory().getItemInMainHand());
            }
        }
    }
    
    /**
     * Kiểm tra block có phải do người chơi đặt không
     * 
     * @param block Block cần kiểm tra
     * @return true nếu block do người chơi đặt
     */
    private boolean isPlacedBlock(Block block) {
        List<MetadataValue> metaDataValues = block.getMetadata("PlacedBlock");
        for (MetadataValue value : metaDataValues) {
            return value.asBoolean();
        }
        return false;
    }
} 