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

import com.hongminh54.storage.Manager.MineManager;
import com.hongminh54.storage.Manager.TNTEnchantManager;
import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.Chat;
import com.hongminh54.storage.Utils.File;
import com.hongminh54.storage.WorldGuard.WorldGuard;

/**
 * Lớp xử lý sự kiện phá block với cúp có phù phép TNT
 */
public class TNTEnchantListener implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockBreak(@NotNull BlockBreakEvent e) {
        Player player = e.getPlayer();
        Block block = e.getBlock();
        ItemStack handItem = player.getInventory().getItemInMainHand();
        
        // Chỉ ghi log khi thực sự cần thiết
        boolean debug = Storage.getStorage().isDebug();
        
        // Kiểm tra điều kiện cơ bản
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        
        if (handItem == null || handItem.getType() == Material.AIR) {
            return;
        }
        
        if (!TNTEnchantManager.isPickaxe(handItem)) {
            return;
        }
        
        // Kiểm tra phù phép TNT trên cúp
        int tntLevel = TNTEnchantManager.getEnchantLevel(handItem);
        if (tntLevel <= 0) {
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
        
        // Kiểm tra block có thể khai thác không
        if (!MineManager.checkBreak(block)) {
            return;
        }
        
        // Đã qua các bước kiểm tra, xử lý phù phép TNT
        if (debug) {
            Storage.getStorage().getLogger().info("TNT Enchant: Bắt đầu xử lý phá block TNT với cấp độ " + tntLevel);
        }
        
        List<Block> blocksToBreak = TNTEnchantManager.handleTNTBreak(player, block, tntLevel);
        
        // Nếu chỉ có 1 block (block chính) thì để hệ thống khác xử lý
        if (blocksToBreak.size() <= 1) {
            return;
        }
        
        if (debug) {
            Storage.getStorage().getLogger().info("TNT Enchant: Xử lý " + blocksToBreak.size() + " block");
        }
        
        // Hủy sự kiện ban đầu vì sẽ được xử lý thủ công
        e.setCancelled(true);
        
        // Xử lý khối ban đầu theo cơ chế auto pickup
        handleBlockBreak(player, blocksToBreak);
        
        // Hiệu ứng âm thanh và hạt
        playEffects(player, block.getLocation());
        
        // Thông báo hiệu ứng phá khối theo cấp độ
        sendEffectMessage(player, tntLevel, blocksToBreak.size());
    }
    
    /**
     * Phát hiệu ứng âm thanh và hạt khi kích hoạt phù phép TNT
     * 
     * @param player Người chơi sử dụng phù phép
     * @param location Vị trí khối bị phá
     */
    private void playEffects(Player player, Location location) {
        // Xử lý âm thanh
        String soundConfig = File.getConfig().getString("tnt_enchant.sound", "ENTITY_GENERIC_EXPLODE:0.3:1.2");
        if (soundConfig != null && !soundConfig.isEmpty()) {
            try {
                String[] soundParts = soundConfig.split(":");
                String soundName = soundParts[0];
                float volume = soundParts.length > 1 ? Float.parseFloat(soundParts[1]) : 0.3f;
                float pitch = soundParts.length > 2 ? Float.parseFloat(soundParts[2]) : 1.2f;
                
                try {
                    // Thử chuẩn hóa tên sound cho 1.12.2
                    Sound sound = Sound.valueOf(soundName);
                    player.playSound(location, sound, volume, pitch);
                } catch (IllegalArgumentException e) {
                    // Fallback cho các phiên bản khác
                    player.playSound(location, soundName, volume, pitch);
                }
            } catch (Exception e) {
                Storage.getStorage().getLogger().warning("Lỗi khi phát âm thanh TNT: " + e.getMessage());
            }
        }
        
        // Xử lý hiệu ứng hạt
        String particleConfig = File.getConfig().getString("tnt_enchant.particle", "EXPLOSION_NORMAL:0.3:0.3:0.3:0.05:5");
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
                    // Chỉ sử dụng enum Particle cho phiên bản mới hơn
                    if (isParticleEnumSupported()) {
                        try {
                            // Thử lấy enum Particle (chỉ hoạt động từ 1.9+)
                            Particle particle = Particle.valueOf(particleName);
                            player.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed);
                        } catch (NoSuchMethodError | IllegalArgumentException e) {
                            // Fallback cho phương thức cũ
                            player.getWorld().spawnParticle(Particle.valueOf(particleName), location, count, 
                                                           offsetX, offsetY, offsetZ, speed);
                        }
                    } else {
                        // Fallback cho 1.8.8 - 1.12.2
                        for (int i = 0; i < count; i++) {
                            double x = location.getX() + (Math.random() * 2 - 1) * offsetX;
                            double y = location.getY() + (Math.random() * 2 - 1) * offsetY;
                            double z = location.getZ() + (Math.random() * 2 - 1) * offsetZ;
                            location.getWorld().playEffect(new Location(location.getWorld(), x, y, z), 
                                                         org.bukkit.Effect.valueOf(convertParticleName(particleName)), 0);
                        }
                    }
                } catch (Exception e) {
                    // Lỗi khi tạo particle, ghi nhận log
                    Storage.getStorage().getLogger().warning("Lỗi khi tạo hiệu ứng hạt TNT: " + e.getMessage());
                }
            } catch (Exception e) {
                Storage.getStorage().getLogger().warning("Lỗi khi xử lý cấu hình hiệu ứng hạt TNT: " + e.getMessage());
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
     * Gửi thông báo hiệu ứng khi sử dụng phù phép TNT
     * 
     * @param player Người chơi nhận thông báo
     * @param level Cấp độ phù phép
     * @param blockCount Số lượng block đã phá
     */
    private void sendEffectMessage(Player player, int level, int blockCount) {
        if (!File.getConfig().getBoolean("tnt_enchant.effect_message.enabled", true)) {
            return;
        }
        
        String format = File.getConfig().getString("tnt_enchant.effect_message.format", 
                                                 "&c⚡ &eCúp TNT %level% &cphá &f%blocks% &cblock!");
        
        if (format != null && !format.isEmpty()) {
            String message = format.replace("%level%", getRomanLevel(level))
                                 .replace("%blocks%", String.valueOf(blockCount));
            
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
     * Xử lý phá các block theo cơ chế auto pickup
     * 
     * @param player Người chơi phá block
     * @param blocks Danh sách các block cần phá
     */
    private void handleBlockBreak(Player player, List<Block> blocks) {
        boolean debug = Storage.getStorage().isDebug();
        
        // Tối ưu: Xử lý hàng loạt để giảm áp lực lên server
        if (MineManager.isAutoPickup(player)) {
            if (debug) Storage.getStorage().getLogger().info("TNT Enchant: Xử lý batch với auto pickup");
            Map<String, Integer> blockResults = MineManager.processBlocksBatchFromList(player, blocks);
            
            // Phá hủy các block đã xử lý (không drop items để tránh dupe)
            for (Block block : blocks) {
                block.setType(Material.AIR);
            }
            
            // Thông báo kết quả
            if (!blockResults.isEmpty() && File.getConfig().getBoolean("tnt_enchant.show_batch_message", true)) {
                StringBuilder message = new StringBuilder(Objects.requireNonNull(
                    File.getConfig().getString("tnt_enchant.batch_message", "&aBạn đã khai thác được:"))
                );
                
                for (Map.Entry<String, Integer> entry : blockResults.entrySet()) {
                    String materialName = MineManager.getMaterialDisplayName(entry.getKey());
                    message.append("\n&7- &e").append(materialName).append(": &a+").append(entry.getValue());
                }
                
                player.sendMessage(Objects.requireNonNull(Chat.colorizewp(message.toString())));
            }
        } else {
            if (debug) Storage.getStorage().getLogger().info("TNT Enchant: Xử lý block thông thường (không auto pickup)");
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