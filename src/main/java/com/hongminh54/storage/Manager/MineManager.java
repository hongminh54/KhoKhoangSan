package com.hongminh54.storage.Manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import com.cryptomorin.xseries.XMaterial;
import com.hongminh54.storage.Database.PlayerData;
import com.hongminh54.storage.NMS.NMSAssistant;
import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.File;
import com.hongminh54.storage.Utils.Number;
import com.hongminh54.storage.Utils.StatsManager;

public class MineManager {

    public static HashMap<String, Integer> playerdata = new HashMap<>();
    public static HashMap<Player, Integer> playermaxdata = new HashMap<>();
    public static HashMap<String, String> blocksdata = new HashMap<>();
    public static HashMap<String, String> blocksdrop = new HashMap<>();
    public static HashMap<Player, Boolean> toggle = new HashMap<>();

    public static int getPlayerBlock(@NotNull Player p, String material) {
        return playerdata.get(p.getName() + "_" + material);
    }

    public static boolean hasPlayerBlock(@NotNull Player p, String material) {
        return playerdata.containsKey(p.getName() + "_" + material);
    }

    public static int getMaxBlock(Player p) {
        return playermaxdata.get(p);
    }

    @Contract(" -> new")
    public static @NotNull List<String> getPluginBlocks() {
        return new ArrayList<>(blocksdata.values());
    }

    public static void addPluginBlocks(String material) {
        blocksdata.put(material, material);

    }

    public static @NotNull PlayerData getPlayerDatabase(@NotNull Player player) {

        PlayerData playerStats = Storage.db.getData(player.getName());

        if (playerStats == null) {
            playerStats = new PlayerData(player.getName(), createNewData(), File.getConfig().getInt("settings.default_max_storage"));
            Storage.db.createTable(playerStats);
            toggle.put(player, File.getConfig().getBoolean("settings.default_auto_pickup"));
        } else {
            toggle.put(player, File.getConfig().getBoolean("settings.default_auto_pickup"));
        }

        return playerStats;
    }

    private static @NotNull String createNewData() {
        StringBuilder mapAsString = new StringBuilder("{");
        for (String block : getPluginBlocks()) {
            mapAsString.append(block).append("=").append(0).append(", ");
        }
        mapAsString.delete(mapAsString.length() - 2, mapAsString.length()).append("}");
        return mapAsString.toString();
    }

    public static @NotNull String convertOfflineData(Player p) {
        StringBuilder mapAsString = new StringBuilder("{");
        for (String block : getPluginBlocks()) {
            if (playerdata.containsKey(p.getName() + "_" + block)) {
                mapAsString.append(block).append("=").append(getPlayerBlock(p, block)).append(", ");
            } else {
                mapAsString.append(block).append("=").append(0).append(", ");
            }
        }
        mapAsString.delete(mapAsString.length() - 2, mapAsString.length()).append("}");
        return mapAsString.toString();
    }

    public static @NotNull List<String> convertOnlineData(@NotNull String data) {
        String data_1 = data.replace("{", "").replace("}", "").replace(" ", "");
        List<String> list = new ArrayList<>();
        List<String> testlist = new ArrayList<>();
        for (String blocklist : data_1.split(",")) {
            String[] block = blocklist.split("=");
            if (getPluginBlocks().contains(block[0])) {
                list.add(block[0] + ";" + block[1]);
                testlist.add(block[0]);
            }
        }
        for (String blocklist : getPluginBlocks()) {
            if (!testlist.contains(blocklist)) {
                list.add(blocklist + ";" + 0);
                testlist.add(blocklist);
            }
        }
        return list;
    }

    public static void setBlock(@NotNull Player p, String material, int amount) {
        playerdata.put(p.getName() + "_" + material, amount);
    }

    public static void setBlock(Player p, @NotNull List<String> list) {
        list.forEach(block -> {
            String[] block_data = block.split(";");
            String material = block_data[0] + ";" + block_data[1];
            int amount = Number.getInteger(block_data[2]);
            setBlock(p, material, amount);
        });
    }

    public static boolean addBlockAmount(Player p, String material, int amount) {
        if (amount > 0) {
            if (blocksdata.containsKey(material) && hasPlayerBlock(p, material)) {
                int old_data = getPlayerBlock(p, material);
                int new_data = old_data + amount;
                int max_storage = getMaxBlock(p);
                if (old_data >= max_storage) return false;
                playerdata.replace(p.getName() + "_" + material, Math.min(new_data, max_storage));
                
                // Ghi nhận hoạt động gửi vào kho
                com.hongminh54.storage.Utils.StatsManager.recordDeposit(p, amount);
                
                return true;
            } else if (blocksdata.containsKey(material) && !hasPlayerBlock(p, material)) {
                setBlock(p, material, amount);
                
                // Ghi nhận hoạt động gửi vào kho
                com.hongminh54.storage.Utils.StatsManager.recordDeposit(p, amount);
                
                return true;
            }
        }
        return false;
    }

    public static boolean removeBlockAmount(Player p, String material, int amount) {
        if (amount > 0) {
            int old_data = getPlayerBlock(p, material);
            int new_data = old_data - amount;
            if (old_data <= 0) return false;
            playerdata.replace(p.getName() + "_" + material, Math.max(new_data, 0));
            
            // Ghi nhận hoạt động rút ra
            com.hongminh54.storage.Utils.StatsManager.recordWithdraw(p, amount);
            
            return true;
        }
        return false;
    }

    /**
     * Load dữ liệu người chơi từ cơ sở dữ liệu
     * @param p Người chơi
     */
    public static void loadPlayerData(@NotNull Player p) {
        try {
            String data = "";
            PlayerData playerData = getPlayerDatabase(p);
            if (playerData != null) {
                data = playerData.getData();
            } else {
                // Tạo mới dữ liệu nếu không tìm thấy
                data = createNewData();
                Storage.getStorage().getLogger().info("Tạo mới dữ liệu cho " + p.getName());
            }
            List<String> list = convertOnlineData(data);
            playermaxdata.put(p, playerData != null ? playerData.getMax() : 0);
            setBlock(p, list);
            
            // Gọi StatsManager để tải dữ liệu thống kê
            Storage.getStorage().getLogger().info("Đang tải dữ liệu thống kê cho " + p.getName());
            if (playerData != null) {
                StatsManager.loadPlayerStats(p);
            } else {
                StatsManager.initPlayerStats(p);
            }
        } catch (Exception ex) {
            Storage.getStorage().getLogger().severe("Lỗi khi tải dữ liệu người chơi " + p.getName() + ": " + ex.getMessage());
            ex.printStackTrace();
            // Khởi tạo dữ liệu mặc định nếu có lỗi
            StatsManager.initPlayerStats(p);
        }
    }

    /**
     * Lưu dữ liệu người chơi vào cơ sở dữ liệu
     * @param p Người chơi
     */
    public static void savePlayerData(@NotNull Player p) {
        try {
            // Lấy dữ liệu hiện tại từ cơ sở dữ liệu
            PlayerData currentData = Storage.db.getData(p.getName());
            if (currentData != null) {
                // Tạo PlayerData mới với dữ liệu kho cập nhật
                PlayerData updatedData = new PlayerData(
                    p.getName(), 
                    convertOfflineData(p), 
                    getMaxBlock(p),
                    currentData.getStatsData() // Giữ nguyên dữ liệu thống kê
                );
                
                // Cập nhật vào cơ sở dữ liệu
                Storage.db.updateTable(updatedData);
            } else {
                // Nếu không có dữ liệu, tạo mới
                PlayerData newData = new PlayerData(
                    p.getName(),
                    convertOfflineData(p),
                    getMaxBlock(p),
                    "{}" // Dữ liệu thống kê mặc định
                );
                Storage.db.createTable(newData);
            }
        } catch (Exception ex) {
            Storage.getStorage().getLogger().severe("Lỗi khi lưu dữ liệu người chơi " + p.getName() + ": " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public static String getDrop(@NotNull Block block) {
        return blocksdrop.get(block.getType() + ";" + (new NMSAssistant().isVersionLessThanOrEqualTo(12) ? block.getData() : "0"));
    }

    public static void loadBlocks() {
        if (!blocksdrop.isEmpty()) {
            blocksdrop.clear();
        }
        if (!blocksdata.isEmpty()) {
            blocksdata.clear();
        }
        for (String block_break : Objects.requireNonNull(File.getConfig().getConfigurationSection("blocks")).getKeys(false)) {
            String item_drop = File.getConfig().getString("blocks." + block_break + ".drop");
            NMSAssistant nms = new NMSAssistant();
            if (item_drop != null) {
                if (!item_drop.contains(";")) {
                    addPluginBlocks(item_drop + ";0");
                    blocksdrop.put(block_break, item_drop + ";0");
                } else {
                    if (nms.isVersionLessThanOrEqualTo(12)) {
                        String[] item_data = item_drop.split(";");
                        String item_material = item_data[0] + ";" + item_data[1];
                        addPluginBlocks(item_material);
                        blocksdrop.put(block_break, item_material);
                    } else {
                        String[] item_data = item_drop.split(";");
                        addPluginBlocks(item_data[0] + ";0");
                        blocksdrop.put(block_break, item_data[0] + ";0");
                    }
                }
            }
        }
    }

    public static boolean checkBreak(@NotNull Block block) {
        if (File.getConfig().contains("blocks." + block.getType().name() + ";" + (new NMSAssistant().isVersionLessThanOrEqualTo(12) ? block.getData() : "0") + ".drop")) {
            return File.getConfig().getString("blocks." + block.getType().name() + ";" + (new NMSAssistant().isVersionLessThanOrEqualTo(12) ? block.getData() : "0") + ".drop") != null;
        } else if (File.getConfig().contains("blocks." + block.getType().name() + ".drop")) {
            return File.getConfig().getString("blocks." + block.getType().name() + ".drop") != null;
        }
        return false;
    }

    public static String getMaterial(String material) {
        String material_data = material.replace(":", ";");
        NMSAssistant nms = new NMSAssistant();
        if (nms.isVersionGreaterThanOrEqualTo(13)) {
            return material_data.split(";")[0] + ";0";
        } else {
            if (Number.getInteger(material_data.split(";")[1]) > 0) {
                return material;
            } else {
                return material_data.split(";")[0] + ";0";
            }
        }
    }

    public static String getItemStackDrop(ItemStack item) {
        for (String drops : getPluginBlocks()) {
            if (drops != null) {
                NMSAssistant nms = new NMSAssistant();
                if (nms.isVersionLessThanOrEqualTo(12)) {
                    Optional<XMaterial> xMaterial = XMaterial.matchXMaterial(drops);
                    if (xMaterial.isPresent()) {
                        ItemStack itemStack = xMaterial.get().parseItem();
                        if (itemStack != null && item.getType().equals(itemStack.getType())) {
                            return drops;
                        }
                    }
                }
                if (drops.contains(";")) {
                    Optional<XMaterial> xMaterial = XMaterial.matchXMaterial(drops.split(";")[0]);
                    if (xMaterial.isPresent()) {
                        ItemStack itemStack = xMaterial.get().parseItem();
                        if (itemStack != null && item.getType().equals(itemStack.getType())) {
                            return drops;
                        }
                    }
                } else {
                    Optional<XMaterial> xMaterial = XMaterial.matchXMaterial(drops);
                    if (xMaterial.isPresent()) {
                        ItemStack itemStack = xMaterial.get().parseItem();
                        if (itemStack != null && item.getType().equals(itemStack.getType())) {
                            return drops;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Kiểm tra trạng thái tự động nhặt vật phẩm của người chơi
     * @param p Người chơi
     * @return true nếu đang bật, false nếu đang tắt
     */
    public static boolean isAutoPickup(@NotNull Player p) {
        return toggle.getOrDefault(p, File.getConfig().getBoolean("settings.default_auto_pickup"));
    }

}
