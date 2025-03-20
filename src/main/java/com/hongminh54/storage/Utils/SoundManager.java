package com.hongminh54.storage.Utils;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Class quản lý âm thanh, đảm bảo tương thích giữa các phiên bản Minecraft
 */
public class SoundManager {
    
    /**
     * Phát âm thanh cho người chơi, tự động chuyển đổi giữa các phiên bản Minecraft
     * 
     * @param player Người chơi nghe âm thanh
     * @param soundName Tên chuỗi của âm thanh (không cần enum)
     * @param volume Âm lượng
     * @param pitch Cao độ
     */
    public static void playSound(Player player, String soundName, float volume, float pitch) {
        if (player == null || soundName == null) return;
        
        try {
            // Xử lý các loại âm thanh theo phiên bản Minecraft
            Sound sound = null;
            
            // Xử lý âm thanh NOTE_XXX đặc biệt
            if (soundName.startsWith("NOTE_")) {
                // Thử tìm với tên gốc trước (NOTE_PLING)
                try {
                    sound = Sound.valueOf(soundName);
                } catch (IllegalArgumentException e) {
                    // Chuyển đổi từ NOTE_PLING sang dạng mới BLOCK_NOTE_BLOCK_PLING
                    String instrument = soundName.replace("NOTE_", "");
                    
                    try {
                        // Thử phiên bản mới (1.13+)
                        sound = Sound.valueOf("BLOCK_NOTE_BLOCK_" + instrument);
                    } catch (IllegalArgumentException e2) {
                        // Nếu không có cả hai định dạng, sử dụng âm thanh thay thế
                        try {
                            // Sử dụng âm thanh thay thế tương tự
                            if (instrument.equals("PLING")) {
                                sound = Sound.valueOf("BLOCK_NOTE_BLOCK_BELL");
                            } else if (instrument.equals("BASS")) {
                                sound = Sound.valueOf("BLOCK_NOTE_BLOCK_BASS");
                            } else if (instrument.equals("SNARE")) {
                                sound = Sound.valueOf("BLOCK_NOTE_BLOCK_SNARE");
                            } else if (instrument.equals("HAT")) {
                                sound = Sound.valueOf("BLOCK_NOTE_BLOCK_HAT");
                            } else if (instrument.equals("HARP")) {
                                sound = Sound.valueOf("BLOCK_NOTE_BLOCK_HARP");
                            } else if (instrument.equals("BASEDRUM")) {
                                sound = Sound.valueOf("BLOCK_NOTE_BLOCK_BASEDRUM");
                            } else {
                                // Nếu không có âm thanh cụ thể, sử dụng âm thanh mặc định
                                sound = Sound.valueOf("ENTITY_EXPERIENCE_ORB_PICKUP");
                            }
                        } catch (IllegalArgumentException e3) {
                            // Nếu tất cả đều thất bại, thử âm thanh phổ biến cuối cùng
                            try {
                                sound = Sound.valueOf("ENTITY_PLAYER_LEVELUP");
                            } catch (Exception e4) {
                                // Bỏ qua nếu không hỗ trợ
                                return;
                            }
                        }
                    }
                }
            } 
            // Xử lý các âm thanh ENTITY_XXX
            else if (soundName.startsWith("ENTITY_")) {
                try {
                    sound = Sound.valueOf(soundName);
                } catch (IllegalArgumentException e) {
                    // Thử chuyển đổi từ ENTITY_EXPERIENCE_ORB_PICKUP thành EXPERIENCE_ORB_PICKUP
                    if (soundName.startsWith("ENTITY_EXPERIENCE_ORB_")) {
                        String action = soundName.replace("ENTITY_EXPERIENCE_ORB_", "");
                        try {
                            sound = Sound.valueOf("EXPERIENCE_ORB_" + action);
                        } catch (IllegalArgumentException e2) {
                            // Bỏ qua nếu không hỗ trợ
                        }
                    } else if (soundName.startsWith("ENTITY_PLAYER_")) {
                        String action = soundName.replace("ENTITY_PLAYER_", "");
                        try {
                            sound = Sound.valueOf("PLAYER_" + action);
                        } catch (IllegalArgumentException e2) {
                            // Bỏ qua nếu không hỗ trợ
                        }
                    } else if (soundName.startsWith("ENTITY_VILLAGER_")) {
                        String action = soundName.replace("ENTITY_VILLAGER_", "");
                        try {
                            sound = Sound.valueOf("VILLAGER_" + action);
                        } catch (IllegalArgumentException e2) {
                            // Bỏ qua nếu không hỗ trợ
                        }
                    }
                }
            }
            // Thử tìm sound trực tiếp
            else {
                try {
                    sound = Sound.valueOf(soundName);
                } catch (IllegalArgumentException e) {
                    // Ghi log lỗi và bỏ qua
                    System.out.println("Không thể phát âm thanh: " + soundName + " - " + e.getMessage());
                    return;
                }
            }
            
            // Phát âm thanh nếu sound không phải null
            if (sound != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        } catch (Exception e) {
            // Xử lý lỗi và ghi log
            System.out.println("Không thể phát âm thanh: " + soundName + " - " + e.getMessage());
        }
    }
    
    /**
     * Phát âm thanh cho người chơi, tự động chuyển đổi giữa các phiên bản Minecraft
     * 
     * @param player Người chơi nghe âm thanh
     * @param sound Enum Sound
     * @param volume Âm lượng
     * @param pitch Cao độ
     */
    public static void playSound(Player player, Sound sound, float volume, float pitch) {
        if (player == null || sound == null) {
            return;
        }
        
        try {
            // Tự động chuyển đổi tên âm thanh
            String soundName = sound.name();
            
            // Thử phát trực tiếp trước
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception e) {
            // Nếu lỗi, thử các phương pháp khác
            try {
                String convertedSound = convertSoundName(sound.name());
                Sound alternativeSound = Sound.valueOf(convertedSound);
                player.playSound(player.getLocation(), alternativeSound, volume, pitch);
            } catch (Exception ex) {
                // Ghi log lỗi
                System.out.println("Không thể phát âm thanh: " + sound.name() + " - " + ex.getMessage());
            }
        }
    }
    
    /**
     * Phát âm thanh tại vị trí cụ thể
     * 
     * @param location Vị trí phát âm thanh
     * @param soundName Tên âm thanh
     * @param volume Âm lượng
     * @param pitch Cao độ
     */
    public static void playSound(Location location, String soundName, float volume, float pitch) {
        if (location == null || location.getWorld() == null || soundName == null || soundName.isEmpty()) {
            return;
        }
        
        try {
            // Thử cách mới trước
            Sound sound = Sound.valueOf(soundName);
            location.getWorld().playSound(location, sound, volume, pitch);
        } catch (Exception e) {
            // Thử với tên đã chuyển đổi
            try {
                String convertedSound = convertSoundName(soundName);
                Sound sound = Sound.valueOf(convertedSound);
                location.getWorld().playSound(location, sound, volume, pitch);
            } catch (Exception ex) {
                // Bỏ qua lỗi nhưng ghi log
                System.out.println("Không thể phát âm thanh tại vị trí: " + soundName);
            }
        }
    }
    
    /**
     * Chuyển đổi tên âm thanh giữa các phiên bản Minecraft
     * 
     * @param soundName Tên âm thanh từ phiên bản mới
     * @return Tên âm thanh tương thích với phiên bản cũ
     */
    private static String convertSoundName(String soundName) {
        // Chuyển đổi từ 1.13+ về 1.12.2 và cũ hơn
        if (soundName.startsWith("BLOCK_NOTE_BLOCK_")) {
            // BLOCK_NOTE_BLOCK_HARP -> NOTE_HARP
            return "NOTE_" + soundName.replace("BLOCK_NOTE_BLOCK_", "");
        } 
        else if (soundName.startsWith("ENTITY_EXPERIENCE_ORB_")) {
            // ENTITY_EXPERIENCE_ORB_PICKUP -> EXPERIENCE_ORB_PICKUP
            return "EXPERIENCE_ORB_" + soundName.replace("ENTITY_EXPERIENCE_ORB_", "");
        } 
        else if (soundName.startsWith("ENTITY_VILLAGER_")) {
            // ENTITY_VILLAGER_NO -> VILLAGER_NO
            return "VILLAGER_" + soundName.replace("ENTITY_VILLAGER_", "");
        } 
        else if (soundName.startsWith("UI_BUTTON_")) {
            // UI_BUTTON_CLICK -> CLICK
            return "CLICK";
        }
        else if (soundName.startsWith("BLOCK_CHEST_")) {
            // BLOCK_CHEST_OPEN -> CHEST_OPEN
            return "CHEST_" + soundName.replace("BLOCK_CHEST_", "");
        }
        else if (soundName.startsWith("ENTITY_PLAYER_")) {
            // ENTITY_PLAYER_LEVELUP -> LEVEL_UP
            String action = soundName.replace("ENTITY_PLAYER_", "");
            if (action.equals("LEVELUP")) {
                return "LEVEL_UP";
            }
            return "PLAYER_" + action;
        }
        
        // Chuyển đổi ngược từ 1.12.2 lên 1.13+
        if (soundName.equals("NOTE_PLING")) {
            return "BLOCK_NOTE_BLOCK_PLING";
        }
        
        // Trả về không đổi nếu không có quy tắc chuyển đổi
        return soundName;
    }
    
    /**
     * Phát âm thanh từ chuỗi cấu hình
     * Ví dụ: "ENTITY_VILLAGER_NO:1.0:1.0" hoặc "NOTE_PLING:0.5:1.2"
     * 
     * @param player Người chơi
     * @param soundConfig Chuỗi cấu hình âm thanh dạng "TÊN_ÂM_THANH:VOLUME:PITCH"
     */
    public static void playSoundFromConfig(Player player, String soundConfig) {
        if (player == null || soundConfig == null || soundConfig.isEmpty()) {
            return;
        }
        
        try {
            String[] parts = soundConfig.split(":");
            String soundName = parts[0];
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
            
            playSound(player, soundName, volume, pitch);
        } catch (Exception e) {
            // Bỏ qua lỗi
            System.out.println("Lỗi khi phát âm thanh từ cấu hình: " + soundConfig);
        }
    }
} 