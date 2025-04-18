package com.hongminh54.storage.Manager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

import org.bukkit.Bukkit;
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
import com.hongminh54.storage.Utils.TransferManager;

public class MineManager {

    public static HashMap<String, Integer> playerdata = new HashMap<>();
    public static HashMap<Player, Integer> playermaxdata = new HashMap<>();
    public static HashMap<String, String> blocksdata = new HashMap<>();
    public static HashMap<String, String> blocksdrop = new HashMap<>();
    public static HashMap<Player, Boolean> toggle = new HashMap<>();

    public static int getPlayerBlock(@NotNull Player p, String material) {
        if (!playerdata.containsKey(p.getName() + "_" + material)) {
            return 0; // Trả về 0 nếu không tìm thấy khóa trong map
        }
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

    /**
     * Lưu dữ liệu người chơi vào cơ sở dữ liệu một cách bất đồng bộ
     * @param p Người chơi
     */
    public static void savePlayerDataAsync(@NotNull Player p) {
        if (p == null) return;
        
        // Lưu dữ liệu cần thiết để sử dụng trong task bất đồng bộ
        final String playerName = p.getName();
        final String playerData = convertOfflineData(p);
        final int maxBlock = getMaxBlock(p);
        
        // Chạy task lưu dữ liệu trong luồng bất đồng bộ
        Bukkit.getScheduler().runTaskAsynchronously(Storage.getStorage(), () -> {
            Connection conn = null;
            PreparedStatement ps = null;
            ResultSet rs = null;
            
            try {
                conn = Storage.db.getConnection();
                if (conn == null) {
                    Storage.getStorage().getLogger().severe("Không thể kết nối đến cơ sở dữ liệu để lưu dữ liệu người chơi");
                    return;
                }
                
                // Kiểm tra xem người chơi đã có dữ liệu chưa
                ps = conn.prepareStatement("SELECT statsData FROM " + Storage.db.table + " WHERE player = ?");
                ps.setString(1, playerName);
                rs = ps.executeQuery();
                
                if (rs.next()) {
                    // Người chơi đã có dữ liệu, cập nhật
                    String statsData = rs.getString("statsData");
                    if (statsData == null) statsData = "{}";
                    
                    rs.close();
                    ps.close();
                    
                    // Cập nhật vào cơ sở dữ liệu
                    ps = conn.prepareStatement("UPDATE " + Storage.db.table + " SET data = ?, max = ? WHERE player = ?");
                    ps.setString(1, playerData);
                    ps.setInt(2, maxBlock);
                    ps.setString(3, playerName);
                    ps.executeUpdate();
                } else {
                    // Người chơi chưa có dữ liệu, tạo mới
                    if (rs != null) rs.close();
                    if (ps != null) ps.close();
                    
                    ps = conn.prepareStatement("INSERT INTO " + Storage.db.table + " (player, data, max, statsData) VALUES (?, ?, ?, '{}')");
                    ps.setString(1, playerName);
                    ps.setString(2, playerData);
                    ps.setInt(3, maxBlock);
                    ps.executeUpdate();
                }
                
                if (Storage.getStorage().getConfig().getBoolean("settings.debug_mode", false)) {
                    Storage.getStorage().getLogger().info("Đã lưu dữ liệu của " + playerName + " vào cơ sở dữ liệu (bất đồng bộ)");
                }
                
            } catch (SQLException e) {
                Storage.getStorage().getLogger().severe("Lỗi khi lưu dữ liệu người chơi " + playerName + ": " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    if (rs != null) rs.close();
                    if (ps != null) ps.close();
                    if (conn != null) Storage.db.returnConnection(conn);
                } catch (SQLException e) {
                    Storage.getStorage().getLogger().log(Level.SEVERE, "Không thể đóng kết nối database", e);
                }
            }
        });
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
        // Chặn các khối gỗ và lá cây không cho vào kho
        String blockType = block.getType().name().toUpperCase();
        if (blockType.contains("LOG") || 
            blockType.contains("WOOD") || 
            blockType.endsWith("_STEM") || 
            blockType.contains("STRIPPED") ||
            blockType.contains("LEAVES") ||
            blockType.equals("LEAVES") ||
            blockType.equals("LEAVES_2")) {
            return false; // Không cho phép đưa gỗ và lá cây vào kho
        }
        
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

    /**
     * Lấy số lượng khối mà người chơi có
     * @param player Người chơi
     * @param materialKey Mã khối (định dạng MATERIAL;DATA)
     * @return Số lượng khối
     */
    public static int getBlockAmount(Player player, String materialKey) {
        if (player == null || materialKey == null) {
            return 0;
        }
        
        if (!playerdata.containsKey(player.getName() + "_" + materialKey)) {
            return 0;
        }
        
        return playerdata.get(player.getName() + "_" + materialKey);
    }
    
    /**
     * Lấy tên hiển thị của vật liệu
     * @param materialKey Mã vật liệu (định dạng MATERIAL;DATA)
     * @return Tên hiển thị của vật liệu
     */
    public static String getMaterialDisplayName(String materialKey) {
        if (materialKey == null) {
            return "Unknown";
        }
        
        String display = File.getConfig().getString("items." + materialKey);
        if (display != null) {
            return display;
        }
        
        // Nếu không có tên tùy chỉnh, trả về tên mặc định
        String[] parts = materialKey.split(";");
        String material = parts[0];
        
        // Chuyển đổi tên vật liệu thành tên hiển thị thân thiện
        String friendlyName = material.replace("_", " ").toLowerCase();
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        
        for (char c : friendlyName.toCharArray()) {
            if (c == ' ') {
                result.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }

    /**
     * Xử lý nhiều block cùng lúc - giảm số lượng truy vấn database
     * @param player Người chơi
     * @param blocksMap Map chứa khối và số lượng (định dạng: materialKey -> count)
     * @return Danh sách các vật liệu đã được thêm thành công
     */
    public static Map<String, Integer> processBlocksBatch(Player player, Map<String, Integer> blocksMap) {
        if (player == null || blocksMap == null || blocksMap.isEmpty()) {
            return new HashMap<>();
        }
        
        // Kiểm tra xem tính năng batch processing có được bật không
        if (!File.getConfig().getBoolean("batch_processing.enabled", true)) {
            // Nếu tắt, xử lý từng block riêng biệt
            Map<String, Integer> successfulBlocks = new HashMap<>();
            for (Map.Entry<String, Integer> entry : blocksMap.entrySet()) {
                String material = entry.getKey();
                int amount = entry.getValue();
                for (int i = 0; i < amount; i++) {
                    if (addBlockAmount(player, material, 1)) {
                        successfulBlocks.put(material, successfulBlocks.getOrDefault(material, 0) + 1);
                    }
                }
            }
            return successfulBlocks;
        }
        
        // Kết quả: các vật liệu đã được thêm thành công
        Map<String, Integer> successfulBlocks = new HashMap<>();
        int totalSuccess = 0;
        
        // Lấy kích thước lô tối đa từ config
        int maxBatchSize = File.getConfig().getInt("batch_processing.max_batch_size", 100);
        
        // Lấy giới hạn kho của người chơi
        int maxStorage = getMaxBlock(player);
        
        // Xử lý từng loại block
        for (Map.Entry<String, Integer> entry : blocksMap.entrySet()) {
            String material = entry.getKey();
            int amount = entry.getValue();
            
            if (amount <= 0 || !blocksdata.containsKey(material)) {
                continue;
            }
            
            // Lấy số lượng hiện tại
            int currentAmount = hasPlayerBlock(player, material) ? getPlayerBlock(player, material) : 0;
            
            // Kiểm tra xem có vượt quá giới hạn không
            if (currentAmount >= maxStorage) {
                // Đã đạt giới hạn, bỏ qua
                continue;
            }
            
            // Tính toán số lượng có thể thêm
            int availableSpace = maxStorage - currentAmount;
            int amountToAdd = Math.min(amount, availableSpace);
            
            // Giới hạn kích thước lô xử lý
            amountToAdd = Math.min(amountToAdd, maxBatchSize);
            
            // Thêm vào kho
            if (hasPlayerBlock(player, material)) {
                int newAmount = currentAmount + amountToAdd;
                playerdata.replace(player.getName() + "_" + material, newAmount);
            } else {
                setBlock(player, material, amountToAdd);
            }
            
            // Ghi nhận thành công
            successfulBlocks.put(material, amountToAdd);
            totalSuccess += amountToAdd;
            
            // Thêm độ trễ nếu được cấu hình - chỉ áp dụng khi xử lý nhiều hơn một loại tài nguyên
            int processDelay = File.getConfig().getInt("batch_processing.process_delay", 10);
            if (processDelay > 0 && blocksMap.size() > 1) {
                try {
                    // Giảm thời gian nghỉ xuống nếu số lượng nhỏ
                    if (amountToAdd < 5) {
                        Thread.sleep(processDelay / 2);
                    } else {
                        Thread.sleep(processDelay);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        // Ghi nhận thống kê nếu có block được thêm
        if (totalSuccess > 0) {
            StatsManager.recordDeposit(player, totalSuccess);
        }
        
        return successfulBlocks;
    }
    
    /**
     * Xử lý nhiều block từ danh sách vật liệu của người chơi
     * @param player Người chơi
     * @param blocks Danh sách các Block
     * @return Danh sách các vật liệu đã được thêm thành công
     */
    public static Map<String, Integer> processBlocksBatchFromList(Player player, List<Block> blocks) {
        if (player == null || blocks == null || blocks.isEmpty()) {
            return new HashMap<>();
        }
        
        // Nhóm các block cùng loại
        Map<String, Integer> blockCounts = new HashMap<>();
        
        for (Block block : blocks) {
            if (!checkBreak(block)) {
                continue;
            }
            
            String resource = getDrop(block);
            if (resource == null) {
                continue;
            }
            
            blockCounts.put(resource, blockCounts.getOrDefault(resource, 0) + 1);
        }
        
        // Xử lý hàng loạt
        return processBlocksBatch(player, blockCounts);
    }
    
    /**
     * Chuyển tài nguyên từ người chơi này sang người chơi khác
     * @param sender Người gửi
     * @param receiver Người nhận
     * @param materials Map chứa các vật liệu và số lượng cần chuyển
     * @return Map các vật liệu đã được chuyển thành công và số lượng
     */
    public static Map<String, Integer> transferResources(Player sender, Player receiver, Map<String, Integer> materials) {
        if (sender == null || receiver == null || materials == null || materials.isEmpty()) {
            return new HashMap<>();
        }
        
        Map<String, Integer> successfulTransfers = new HashMap<>();
        
        for (Map.Entry<String, Integer> entry : materials.entrySet()) {
            String material = entry.getKey();
            int amount = entry.getValue();
            
            if (amount <= 0) continue;
            
            // Kiểm tra xem người gửi có đủ tài nguyên không
            if (getBlockAmount(sender, material) >= amount) {
                // Trừ tài nguyên từ người gửi
                if (removeBlockAmount(sender, material, amount)) {
                    // Thêm tài nguyên cho người nhận
                    addBlockAmount(receiver, material, amount);
                    
                    // Thêm vào danh sách chuyển thành công
                    successfulTransfers.put(material, amount);
                    
                    // Ghi lại lịch sử chuyển khoản (sử dụng phiên bản bất đồng bộ)
                    TransferManager.recordTransferAsync(sender.getName(), receiver.getName(), material, amount);
                    
                    // Ghi log nếu cần
                    if (Storage.getStorage().getConfig().getBoolean("settings.debug_mode", false)) {
                        Storage.getStorage().getLogger().info(
                            "Chuyển khoản: " + sender.getName() + " -> " + receiver.getName() + 
                            ", " + material + " x" + amount
                        );
                    }
                }
            }
        }
        
        return successfulTransfers;
    }

}
