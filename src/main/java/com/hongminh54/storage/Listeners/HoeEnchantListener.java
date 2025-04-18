package com.hongminh54.storage.Listeners;

import java.util.Random;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.jetbrains.annotations.NotNull;

import com.hongminh54.storage.Manager.HoeEnchantManager;
import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.Chat;
import com.hongminh54.storage.Utils.File;
import com.hongminh54.storage.WorldGuard.WorldGuard;

/**
 * Lớp xử lý sự kiện phá block với cuốc có phù phép
 */
public class HoeEnchantListener implements Listener {
    
    private final Random random = new Random();

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
        
        if (!HoeEnchantManager.isHoe(handItem)) {
            return;
        }
        
        // Kiểm tra xem block có phải là cây trồng không
        if (!HoeEnchantManager.isCrop(block)) {
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
        
        // Đây là cây trồng, kiểm tra từng phù phép
        boolean debug = Storage.getStorage().isDebug();
        
        // Kiểm tra phù phép Nông Dân Chuyên Nghiệp (Farmer's Touch)
        int farmersTouchLevel = HoeEnchantManager.getEnchantLevel(handItem, HoeEnchantManager.FARMERS_TOUCH);
        if (farmersTouchLevel > 0) {
            if (debug) {
                Storage.getStorage().getLogger().info("HoeEnchant: Bắt đầu xử lý phù phép Nông Dân Chuyên Nghiệp với cấp độ " + farmersTouchLevel);
            }
            
            // Xử lý tự động trồng lại
            boolean autoReplanted = HoeEnchantManager.handleFarmersTouch(player, block, farmersTouchLevel);
            
            if (autoReplanted) {
                playEffects(player, block.getLocation(), "farmers_touch");
                sendEffectMessage(player, farmersTouchLevel, "farmers_touch");
            }
        }
        
        // Kiểm tra phù phép Đất Màu Mỡ (Fertile Soil)
        int fertileSoilLevel = HoeEnchantManager.getEnchantLevel(handItem, HoeEnchantManager.FERTILE_SOIL);
        if (fertileSoilLevel > 0) {
            if (debug) {
                Storage.getStorage().getLogger().info("HoeEnchant: Bắt đầu xử lý phù phép Đất Màu Mỡ với cấp độ " + fertileSoilLevel);
            }
            
            boolean soilEnhanced = HoeEnchantManager.handleFertileSoil(player, block, fertileSoilLevel);
            
            if (soilEnhanced) {
                playEffects(player, block.getLocation(), "fertile_soil");
                sendEffectMessage(player, fertileSoilLevel, "fertile_soil");
            }
        }
        
        // Kiểm tra phù phép Kinh Nghiệm Nông Dân (Farmer's Wisdom)
        int farmersWisdomLevel = HoeEnchantManager.getEnchantLevel(handItem, HoeEnchantManager.FARMERS_WISDOM);
        if (farmersWisdomLevel > 0) {
            if (debug) {
                Storage.getStorage().getLogger().info("HoeEnchant: Bắt đầu xử lý phù phép Kinh Nghiệm Nông Dân với cấp độ " + farmersWisdomLevel);
            }
            
            // Tính toán lượng kinh nghiệm bổ sung
            int baseXp = 1; // Kinh nghiệm cơ bản khi thu hoạch cây trồng
            int bonusXp = (int) (baseXp * (farmersWisdomLevel * 0.5)); // Tăng 50% mỗi cấp độ
            
            // Tạo kinh nghiệm ở vị trí block
            if (bonusXp > 0) {
                Location loc = block.getLocation().add(0.5, 0.5, 0.5);
                loc.getWorld().spawn(loc, ExperienceOrb.class).setExperience(bonusXp);
                
                playEffects(player, block.getLocation(), "farmers_wisdom");
                sendEffectMessage(player, farmersWisdomLevel, "farmers_wisdom");
            }
        }
        
        // Kiểm tra phù phép Tái Sinh (Regeneration)
        int regenerationLevel = HoeEnchantManager.getEnchantLevel(handItem, HoeEnchantManager.REGENERATION);
        if (regenerationLevel > 0) {
            if (debug) {
                Storage.getStorage().getLogger().info("HoeEnchant: Bắt đầu xử lý phù phép Tái Sinh với cấp độ " + regenerationLevel);
            }
            
            // Có cơ hội hồi phục độ bền của cuốc
            if (handItem.getDurability() > 0 && random.nextInt(100) < (5 * regenerationLevel)) { // 5/10/15% cơ hội tùy cấp độ
                short newDurability = (short) Math.max(0, handItem.getDurability() - (2 * regenerationLevel));
                handItem.setDurability(newDurability);
                
                playEffects(player, player.getLocation(), "regeneration");
                sendEffectMessage(player, regenerationLevel, "regeneration");
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
        String soundConfig = File.getConfig().getString("hoe_enchant." + enchantType + ".sound", "ENTITY_EXPERIENCE_ORB_PICKUP:0.3:1.2");
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
        String particleConfig = File.getConfig().getString("hoe_enchant." + enchantType + ".particle", "VILLAGER_HAPPY:0.3:0.3:0.3:0.05:5");
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
            case "EXPLOSION_NORMAL": return "EXPLOSION";
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
     * @param enchantType Loại phù phép
     */
    private void sendEffectMessage(Player player, int level, String enchantType) {
        if (!File.getConfig().getBoolean("hoe_enchant." + enchantType + ".effect_message.enabled", true)) {
            return;
        }
        
        String format = File.getConfig().getString("hoe_enchant." + enchantType + ".effect_message.format", 
                                                 "&a✿ &ePhù phép %enchant% %level% &akích hoạt!");
        
        if (format != null && !format.isEmpty()) {
            String enchantName = getEnchantDisplayName(enchantType);
            String message = format.replace("%level%", getRomanLevel(level))
                                 .replace("%enchant%", enchantName);
            
            player.sendMessage(Chat.colorizewp(message));
        }
    }
    
    /**
     * Lấy tên hiển thị cho loại phù phép
     */
    private String getEnchantDisplayName(String enchantType) {
        switch (enchantType) {
            case HoeEnchantManager.FARMERS_TOUCH: return "Nông Dân Chuyên Nghiệp";
            case HoeEnchantManager.FERTILE_SOIL: return "Đất Màu Mỡ";
            case HoeEnchantManager.FARMERS_WISDOM: return "Kinh Nghiệm Nông Dân";
            case HoeEnchantManager.REGENERATION: return "Tái Sinh";
            default: return enchantType;
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
     * Kiểm tra xem block có phải do người chơi đặt không
     */
    private boolean isPlacedBlock(Block block) {
        for (MetadataValue meta : block.getMetadata("placed")) {
            if (meta.asBoolean()) {
                return true;
            }
        }
        return false;
    }
} 