package com.hongminh54.storage.Utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.hongminh54.storage.Storage;

/**
 * Lớp tiện ích để lấy đầu của người chơi với skin chính xác
 */
public class SkullUtils {
    
    // Cache lưu texture của người chơi để tránh gọi API quá nhiều
    private static final Map<String, String> textureCache = new HashMap<>();
    private static final Map<String, Long> cacheTimestamps = new HashMap<>();
    
    // Thời gian cache hợp lệ (60 phút)
    private static final long CACHE_EXPIRY = TimeUnit.MINUTES.toMillis(60);
    
    // Kiểm tra xem SkinsRestorer có sẵn không
    private static final boolean hasSkinsRestorer = Bukkit.getPluginManager().getPlugin("SkinsRestorer") != null;
    
    // Tên các phương thức và trường từ các phiên bản Minecraft khác nhau
    private static final String SKULL_MATERIAL = getMaterialName();
    
    /**
     * Xác định tên Material cho đầu người chơi dựa trên phiên bản Minecraft
     */
    private static String getMaterialName() {
        // Luôn trả về SKULL_ITEM cho phiên bản 1.12.2
        return "SKULL_ITEM";
    }
    
    /**
     * Tạo đầu của người chơi với skin từ Mojang API hoặc SkinsRestorer
     * 
     * @param playerName Tên của người chơi
     * @return ItemStack là đầu của người chơi với skin chính xác
     */
    public static ItemStack getPlayerHead(String playerName) {
        // Tạo đầu người chơi dựa trên phiên bản Minecraft
        ItemStack skull;
        if (SKULL_MATERIAL.equals("PLAYER_HEAD")) {
            skull = new ItemStack(Material.valueOf(SKULL_MATERIAL), 1);
        } else {
            skull = new ItemStack(Material.valueOf(SKULL_MATERIAL), 1, (short) 3);
        }
        
        if (playerName == null || playerName.isEmpty()) {
            return skull;
        }
        
        // Thử lấy từ cache trước
        String cachedTexture = getCachedTexture(playerName);
        if (cachedTexture != null) {
            return createCustomSkull(cachedTexture);
        }
        
        // Thử sử dụng SkinsRestorer nếu có
        if (hasSkinsRestorer) {
            ItemStack skinsRestorerSkull = getSkullFromSkinsRestorer(playerName);
            if (skinsRestorerSkull != null) {
                return skinsRestorerSkull;
            }
        }
        
        // Thử lấy từ Mojang API
        try {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            String uuid = offlinePlayer.getUniqueId().toString().replace("-", "");
            
            // Lấy texture từ Mojang API
            String texture = getTextureFromMojangAPI(uuid);
            if (texture != null) {
                // Lưu vào cache
                textureCache.put(playerName.toLowerCase(), texture);
                cacheTimestamps.put(playerName.toLowerCase(), System.currentTimeMillis());
                
                return createCustomSkull(texture);
            }
        } catch (Exception e) {
            Storage.getStorage().getLogger().log(Level.WARNING, "Không thể lấy skin từ Mojang API cho " + playerName, e);
        }
        
        // Fallback - thử thiết lập owner thông thường
        try {
            SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
            if (skullMeta != null) {
                skullMeta.setOwner(playerName);
                skull.setItemMeta(skullMeta);
            }
        } catch (Exception e) {
            Storage.getStorage().getLogger().log(Level.WARNING, "Không thể đặt chủ sở hữu đầu thông thường cho " + playerName, e);
        }
        
        return skull;
    }
    
    /**
     * Tạo đầu với texture tùy chỉnh từ chuỗi Base64
     * 
     * @param texture Texture Base64 từ Mojang API
     * @return ItemStack là đầu với texture đã xác định
     */
    private static ItemStack createCustomSkull(String texture) {
        // Tạo đầu người chơi dựa trên phiên bản Minecraft
        ItemStack skull;
        if (SKULL_MATERIAL.equals("PLAYER_HEAD")) {
            skull = new ItemStack(Material.valueOf(SKULL_MATERIAL), 1);
        } else {
            skull = new ItemStack(Material.valueOf(SKULL_MATERIAL), 1, (short) 3);
        }
        
        if (texture == null || texture.isEmpty()) {
            return skull;
        }
        
        try {
            SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
            if (skullMeta == null) {
                return skull;
            }
            
            // Thiết lập texture bằng reflection
            setSkullTexture(skullMeta, texture);
            skull.setItemMeta(skullMeta);
            
            return skull;
        } catch (Exception e) {
            Storage.getStorage().getLogger().log(Level.WARNING, "Không thể tạo đầu tùy chỉnh từ texture", e);
            return skull;
        }
    }
    
    /**
     * Thiết lập texture cho SkullMeta bằng cách sử dụng reflection
     * 
     * @param skullMeta SkullMeta cần thiết lập texture
     * @param texture Chuỗi texture dạng Base64
     */
    private static void setSkullTexture(SkullMeta skullMeta, String texture) {
        try {
            // Sử dụng reflection để thiết lập texture
            // Đoạn mã này tương thích với tất cả các phiên bản Minecraft
            
            // Tạo GameProfile (thông qua reflection để hỗ trợ tất cả các phiên bản)
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            
            // Tạo một UUID ngẫu nhiên cho GameProfile
            Object gameProfile = gameProfileClass.getConstructor(UUID.class, String.class)
                    .newInstance(UUID.randomUUID(), null);
            
            // Lấy và thao tác với properties
            Object properties = gameProfileClass.getMethod("getProperties").invoke(gameProfile);
            Object property = propertyClass.getConstructor(String.class, String.class)
                    .newInstance("textures", texture);
            
            // Thêm property vào properties
            properties.getClass().getMethod("put", Object.class, Object.class)
                    .invoke(properties, "textures", property);
            
            // Thiết lập profile cho SkullMeta
            Field profileField = skullMeta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(skullMeta, gameProfile);
            
        } catch (Exception e) {
            Storage.getStorage().getLogger().log(Level.WARNING, "Không thể đặt texture cho đầu: " + e.getMessage());
        }
    }
    
    /**
     * Lấy texture từ cache nếu còn hợp lệ
     * 
     * @param playerName Tên người chơi cần kiểm tra
     * @return Texture cache hoặc null nếu cache đã hết hạn
     */
    private static String getCachedTexture(String playerName) {
        String lowerName = playerName.toLowerCase();
        if (textureCache.containsKey(lowerName) && cacheTimestamps.containsKey(lowerName)) {
            long timestamp = cacheTimestamps.get(lowerName);
            if (System.currentTimeMillis() - timestamp < CACHE_EXPIRY) {
                return textureCache.get(lowerName);
            } else {
                // Cache đã hết hạn
                textureCache.remove(lowerName);
                cacheTimestamps.remove(lowerName);
            }
        }
        return null;
    }
    
    /**
     * Lấy texture từ Mojang API dựa trên UUID của người chơi
     * 
     * @param uuid UUID của người chơi dạng chuỗi không có dấu gạch ngang
     * @return Texture dạng Base64 hoặc null nếu không lấy được
     */
    private static String getTextureFromMojangAPI(String uuid) {
        try {
            // Gọi Mojang API để lấy thông tin session
            URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int status = connection.getResponseCode();
            if (status != 200) {
                return null;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            // Parse JSON response
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(response.toString());
            
            if (jsonObject.containsKey("properties")) {
                JSONArray properties = (JSONArray) jsonObject.get("properties");
                for (Object propertyObj : properties) {
                    JSONObject property = (JSONObject) propertyObj;
                    if (property.containsKey("name") && property.get("name").equals("textures")) {
                        return (String) property.get("value");
                    }
                }
            }
        } catch (Exception e) {
            Storage.getStorage().getLogger().log(Level.WARNING, "Lỗi khi lấy texture từ Mojang API: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Lấy đầu từ SkinsRestorer plugin nếu có
     * 
     * @param playerName Tên người chơi
     * @return ItemStack là đầu với skin từ SkinsRestorer hoặc null nếu không lấy được
     */
    private static ItemStack getSkullFromSkinsRestorer(String playerName) {
        try {
            if (!hasSkinsRestorer) {
                return null;
            }
            
            // Sử dụng reflection để gọi API của SkinsRestorer
            Class<?> skinsRestorerAPIClass = Class.forName("net.skinsrestorer.api.SkinsRestorerAPI");
            Method getApiMethod = skinsRestorerAPIClass.getMethod("getApi");
            Object skinsRestorerAPI = getApiMethod.invoke(null);
            
            // Kiểm tra xem người chơi có skin tùy chỉnh không
            Method hasSkinMethod = skinsRestorerAPIClass.getMethod("hasSkin", String.class);
            Boolean hasSkin = (Boolean) hasSkinMethod.invoke(skinsRestorerAPI, playerName);
            
            if (Boolean.TRUE.equals(hasSkin)) {
                try {
                    // Thử lấy skin data từ phiên bản mới của API
                    Method getSkinDataMethod = skinsRestorerAPIClass.getMethod("getSkinData", String.class);
                    Object property = getSkinDataMethod.invoke(skinsRestorerAPI, playerName);
                    
                    if (property != null) {
                        Method getValueMethod = property.getClass().getMethod("getValue");
                        String texture = (String) getValueMethod.invoke(property);
                        
                        // Lưu vào cache
                        textureCache.put(playerName.toLowerCase(), texture);
                        cacheTimestamps.put(playerName.toLowerCase(), System.currentTimeMillis());
                        
                        return createCustomSkull(texture);
                    }
                } catch (NoSuchMethodException e) {
                    // API cũ, thử phương thức khác
                    try {
                        Method getSkinMethod = skinsRestorerAPIClass.getMethod("getSkinName", String.class);
                        String skinName = (String) getSkinMethod.invoke(skinsRestorerAPI, playerName);
                        
                        if (skinName != null && !skinName.isEmpty()) {
                            // Lấy UUID của skin
                            Method getUUIDMethod = skinsRestorerAPIClass.getMethod("getUUID", String.class);
                            String skinUUID = (String) getUUIDMethod.invoke(skinsRestorerAPI, skinName);
                            
                            if (skinUUID != null && !skinUUID.isEmpty()) {
                                // Lấy texture từ Mojang API
                                String texture = getTextureFromMojangAPI(skinUUID);
                                if (texture != null) {
                                    // Lưu vào cache
                                    textureCache.put(playerName.toLowerCase(), texture);
                                    cacheTimestamps.put(playerName.toLowerCase(), System.currentTimeMillis());
                                    
                                    return createCustomSkull(texture);
                                }
                            }
                        }
                    } catch (Exception ex) {
                        // Bỏ qua, có thể không có phương thức này
                    }
                }
            }
        } catch (Exception e) {
            // Bỏ qua không ghi log để tránh spam console
            if (Storage.getStorage().getLogger().isLoggable(Level.FINE)) {
                Storage.getStorage().getLogger().log(Level.FINE, "Không thể lấy skin từ SkinsRestorer: " + e.getMessage());
            }
        }
        
        return null;
    }
    
    /**
     * Xóa cache texture để buộc lấy lại từ API
     */
    public static void clearCache() {
        textureCache.clear();
        cacheTimestamps.clear();
    }
    
    /**
     * Áp dụng texture người chơi vào đầu (SKULL_ITEM)
     * 
     * @param skull ItemStack đầu người chơi
     * @param playerName Tên người chơi
     * @return ItemStack đã cập nhật
     */
    public static ItemStack applyPlayerTextureToSkull(ItemStack skull, String playerName) {
        if (skull == null || playerName == null || playerName.isEmpty()) {
            return skull;
        }
        
        try {
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwner(playerName);
                skull.setItemMeta(meta);
            }
        } catch (Exception e) {
            Storage.getStorage().getLogger().log(Level.WARNING, "Không thể đặt chủ sở hữu đầu: " + e.getMessage());
        }
        
        return skull;
    }
} 