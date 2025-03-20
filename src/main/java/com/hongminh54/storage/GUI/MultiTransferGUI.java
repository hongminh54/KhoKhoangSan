package com.hongminh54.storage.GUI;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import com.hongminh54.storage.GUI.manager.IGUI;
import com.hongminh54.storage.GUI.manager.InteractiveItem;
import com.hongminh54.storage.Manager.ItemManager;
import com.hongminh54.storage.Manager.MineManager;
import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.Chat;
import com.hongminh54.storage.Utils.File;
import com.hongminh54.storage.Utils.GUIText;
import com.hongminh54.storage.Utils.SoundManager;

/**
 * Giao diện chuyển nhiều loại tài nguyên cùng lúc
 */
public class MultiTransferGUI implements IGUI, Listener {

    private final Player sender;
    private final Player receiver;
    private final Map<String, Integer> transferItems = new HashMap<>();
    private final FileConfiguration config;
    private Inventory inventory;
    private boolean listenerRegistered = false;

    private static final int GUI_UPDATE_INTERVAL = 5; // Ticks giữa mỗi lần cập nhật GUI
    private static final int MAX_VISIBLE_RESOURCES = 28; // Số lượng tài nguyên tối đa hiển thị mỗi trang
    private static long lastSoundPlayed = 0; // Thời gian phát âm thanh gần nhất
    private static final long SOUND_COOLDOWN = 100; // Thời gian chờ giữa các lần phát âm thanh (milliseconds)
    
    // Cache các ItemStack để tránh tạo mới liên tục
    private static final Map<String, ItemStack> buttonCache = new HashMap<>();
    
    // Tạo và lưu các nút điều hướng vào cache để tái sử dụng
    private static ItemStack getCachedButton(String key, Material material, String name, String... lore) {
        return buttonCache.computeIfAbsent(key, k -> {
            ItemStack button = new ItemStack(material);
            ItemMeta meta = button.getItemMeta();
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            button.setItemMeta(meta);
            return button;
        });
    }
    
    // Phát âm thanh với cooldown
    private void playSound(Player player, Sound sound, float volume, float pitch) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSoundPlayed >= SOUND_COOLDOWN) {
            player.playSound(player.getLocation(), sound, volume, pitch);
            lastSoundPlayed = currentTime;
        }
    }

    /**
     * Khởi tạo giao diện chuyển nhiều loại tài nguyên
     * @param sender Người gửi
     * @param receiver Người nhận
     */
    public MultiTransferGUI(Player sender, Player receiver) {
        this.sender = sender;
        this.receiver = receiver;
        this.config = File.getConfig();
        
        // Đăng ký Listener khi tạo GUI
        registerListener();
        
        // Bắt đầu task cập nhật GUI mỗi 5 giây
        startRefreshTask();
    }
    
    /**
     * Đăng ký listener cho GUI
     */
    private void registerListener() {
        if (!listenerRegistered) {
            Bukkit.getPluginManager().registerEvents(this, Storage.getStorage());
            listenerRegistered = true;
        }
    }
    
    /**
     * Hủy đăng ký listener
     */
    private void unregisterListener() {
        if (listenerRegistered) {
            HandlerList.unregisterAll(this);
            listenerRegistered = false;
        }
    }

    // Biến tham chiếu đến task cập nhật
    private int refreshTaskId = -1;
    
    /**
     * Bắt đầu task tự động cập nhật GUI
     */
    private void startRefreshTask() {
        refreshTaskId = Bukkit.getScheduler().runTaskTimer(Storage.getStorage(), this::refreshGUI, 100L, 100L).getTaskId(); // 100 ticks = 5 giây
    }
    
    /**
     * Dừng task cập nhật GUI
     */
    private void stopRefreshTask() {
        if (refreshTaskId != -1) {
            Bukkit.getScheduler().cancelTask(refreshTaskId);
            refreshTaskId = -1;
        }
    }
    
    /**
     * Cập nhật giao diện để hiển thị thông tin mới nhất
     */
    private void refreshGUI() {
        // Chỉ cập nhật nếu người chơi vẫn mở inventory của chúng ta
        if (sender.isOnline() && sender.getOpenInventory() != null && 
            sender.getOpenInventory().getTopInventory().equals(inventory)) {
            
            // Cập nhật thông tin tài nguyên
            for (Map.Entry<String, Integer> entry : new HashMap<>(transferItems).entrySet()) {
                String material = entry.getKey();
                int amount = entry.getValue();
                
                // Kiểm tra lại số lượng hiện tại người chơi có
                int currentAmount = MineManager.getPlayerBlock(sender, material);
                
                // Nếu số lượng hiện tại ít hơn số lượng đã chọn, cập nhật lại
                if (currentAmount < amount) {
                    if (currentAmount <= 0) {
                        transferItems.remove(material);
                    } else {
                        transferItems.put(material, currentAmount);
                    }
                }
            }
            
            // Cập nhật inventory
            sender.openInventory(getInventory());
        }
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        // Tạo tiêu đề giao diện
        String title = "&8Chuyển nhiều tài nguyên cho &a" + receiver.getName();
        inventory = Bukkit.createInventory(sender, 54, GUIText.format(title));
        
        // Thêm viền trang trí cho GUI
        addBorder();
        
        // Thêm thông tin người chơi
        addPlayerInfo();
        
        // Thêm các tài nguyên
        addResourceItems();
        
        // Thêm nút xác nhận và hủy
        addControlButtons();
        
        return inventory;
    }
    
    /**
     * Thêm viền trang trí cho GUI
     */
    private void addBorder() {
        try {
            // Sử dụng STAINED_GLASS_PANE với data 15 (màu đen) cho viền
            ItemStack borderItem = new ItemStack(Material.valueOf("STAINED_GLASS_PANE"), 1, (short) 15);
            ItemMeta borderMeta = borderItem.getItemMeta();
            borderMeta.setDisplayName(Chat.colorize("&8"));
            borderItem.setItemMeta(borderMeta);
            
            // Thêm viền cho hàng đầu tiên và cuối cùng
            for (int i = 0; i < 9; i++) {
                inventory.setItem(i, borderItem);
                inventory.setItem(45 + i, borderItem);
            }
            
            // Thêm viền cho cột đầu tiên và cuối cùng (trừ các góc đã thêm)
            for (int i = 1; i < 5; i++) {
                inventory.setItem(i * 9, borderItem);
                inventory.setItem(i * 9 + 8, borderItem);
            }
            
            // Sử dụng màu xám cho các ô trang trí khác
            ItemStack decorItem = new ItemStack(Material.valueOf("STAINED_GLASS_PANE"), 1, (short) 7);
            ItemMeta decorMeta = decorItem.getItemMeta();
            decorMeta.setDisplayName(Chat.colorize("&7"));
            decorItem.setItemMeta(decorMeta);
            
            // Thêm các ô trang trí phân cách trong GUI
            inventory.setItem(17, decorItem);
            inventory.setItem(18, decorItem);
            inventory.setItem(26, decorItem);
            inventory.setItem(27, decorItem);
            inventory.setItem(35, decorItem);
            inventory.setItem(36, decorItem);
            inventory.setItem(44, decorItem);
        } catch (Exception e) {
            // Fallback nếu có lỗi với vật liệu
            ItemStack fallbackItem = new ItemStack(Material.STONE, 1);
            ItemMeta meta = fallbackItem.getItemMeta();
            meta.setDisplayName(" ");
            fallbackItem.setItemMeta(meta);
            
            // Thêm viền cho hàng đầu tiên và cuối cùng
            for (int i = 0; i < 9; i++) {
                inventory.setItem(i, fallbackItem);
                inventory.setItem(45 + i, fallbackItem);
            }
            
            // Thêm viền cho cột đầu tiên và cuối cùng (trừ các góc đã thêm)
            for (int i = 1; i < 5; i++) {
                inventory.setItem(i * 9, fallbackItem);
                inventory.setItem(i * 9 + 8, fallbackItem);
            }
        }
    }
    
    /**
     * Thêm thông tin người chơi
     */
    private void addPlayerInfo() {
        // Thêm thông tin người chơi nhận, sử dụng EMERALD thay vì đầu người chơi
        ItemStack playerItem = ItemManager.createItem(
            Material.EMERALD, 
            "&a&l" + receiver.getName(), 
            Arrays.asList(
                "&7Người nhận: &a" + receiver.getName(),
                "&7",
                "&eChọn tài nguyên muốn chuyển bên dưới"
            )
        );
        
        inventory.setItem(4, playerItem);
        
        // Thêm hướng dẫn sử dụng
        ItemStack helpItem = ItemManager.createItem(
                Material.BOOK, 
                "&e&lHướng dẫn sử dụng", 
                Arrays.asList(
                    "&7Nhấp chuột trái vào tài nguyên để chọn",
                    "&7Nhấp chuột trái + Shift để tăng nhanh số lượng",
                    "&7Nhấp chuột phải để giảm số lượng",
                    "&7Shift + Nhấp chuột phải để hủy chọn",
                    "&7",
                    "&eKhi đã chọn xong, nhấp vào nút Xác nhận"
                )
        );
        
        inventory.setItem(49, helpItem);
    }
    
    /**
     * Thêm các tài nguyên vào GUI
     */
    private void addResourceItems() {
        List<String> materialList = MineManager.getPluginBlocks();
        
        // Sắp xếp các slot trong 4 hàng tại giữa (chừa ra viền)
        int[] slots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };
        
        int slotIndex = 0;
        
        for (String material : materialList) {
            int currentAmount = MineManager.getPlayerBlock(sender, material);
            String materialName = File.getConfig().getString("items." + material, material.split(";")[0]);
            
            // Nếu có tài nguyên và còn slot trống, thêm vào giao diện
            if (currentAmount > 0 && slotIndex < slots.length) {
                List<String> lore = new ArrayList<>();
                lore.add("&7Số lượng hiện có: &a" + currentAmount);
                
                // Kiểm tra xem có được chọn chưa
                if (transferItems.containsKey(material)) {
                    int selectedAmount = transferItems.get(material);
                    lore.add("&7Số lượng đã chọn: &e" + selectedAmount);
                    lore.add("&7");
                    lore.add("&aNhấp chuột trái &7để tăng số lượng");
                    lore.add("&cNhấp chuột phải &7để giảm số lượng");
                    lore.add("&eShift + Nhấp chuột phải &7để hủy chọn");
                } else {
                    lore.add("&7");
                    lore.add("&aNhấp để chọn tài nguyên này");
                }
                
                Material itemMaterial;
                short data = 0;
                try {
                    String[] parts = material.split(";");
                    itemMaterial = Material.valueOf(parts[0]);
                    if (parts.length > 1) {
                        data = Short.parseShort(parts[1]);
                    }
                } catch (Exception e) {
                    // Fallback to stone if material is invalid
                    itemMaterial = Material.STONE;
                }
                
                ItemStack itemStack = new ItemStack(itemMaterial, 1, data);
                ItemMeta meta = itemStack.getItemMeta();
                meta.setDisplayName(Chat.colorize("&b" + materialName));
                meta.setLore(Chat.colorizewp(lore));
                itemStack.setItemMeta(meta);
                
                int slot = slots[slotIndex];
                InteractiveItem item = new InteractiveItem(itemStack, slot);
                
                // Xử lý khi nhấp chuột
                item.onClick((player, clickType) -> {
                    if (clickType.isLeftClick()) {
                        // Tăng số lượng hoặc thêm mới
                        int amount = transferItems.getOrDefault(material, 0);
                        
                        // Tính toán số lượng cần tăng
                        int increment = 1;
                        if (clickType.isShiftClick()) {
                            increment = 10; // Shift+Click để tăng nhanh
                        }
                        
                        // Kiểm tra số lượng tối đa
                        int maxAmount = MineManager.getPlayerBlock(player, material);
                        amount = Math.min(amount + increment, maxAmount);
                        
                        if (amount > 0) {
                            transferItems.put(material, amount);
                        }
                        
                        // Cập nhật giao diện
                        player.openInventory(getInventory());
                    } else if (clickType.isRightClick()) {
                        if (transferItems.containsKey(material)) {
                            if (clickType.isShiftClick()) {
                                // Shift+Click phải để hủy chọn
                                transferItems.remove(material);
                            } else {
                                // Click phải để giảm số lượng
                                int amount = transferItems.get(material);
                                
                                // Tính toán số lượng cần giảm
                                int decrement = 1;
                                if (amount > 10) {
                                    decrement = 5; // Giảm nhanh nếu số lượng lớn
                                }
                                
                                amount = Math.max(0, amount - decrement);
                                
                                if (amount > 0) {
                                    transferItems.put(material, amount);
                                } else {
                                    transferItems.remove(material);
                                }
                            }
                            
                            // Cập nhật giao diện
                            player.openInventory(getInventory());
                        }
                    }
                });
                
                inventory.setItem(slot, item);
                slotIndex++;
            }
        }
    }
    
    /**
     * Thêm nút xác nhận và hủy
     */
    private void addControlButtons() {
        // Thêm nút xác nhận nếu đã chọn tài nguyên
        if (!transferItems.isEmpty()) {
            // Tính tổng số lượng đã chọn
            int totalItems = 0;
            for (int amount : transferItems.values()) {
                totalItems += amount;
            }
            
            // Thay thế hướng dẫn sử dụng bằng nút xác nhận
            ItemStack confirmItem = ItemManager.createItem(
                    Material.EMERALD_BLOCK, 
                    "&a&lXác nhận chuyển", 
                    Arrays.asList(
                        "&7Chuyển &e" + transferItems.size() + " &7loại tài nguyên",
                        "&7Tổng số: &f" + totalItems + " &7tài nguyên",
                        "&7Đến: &a" + receiver.getName(),
                        "&7",
                        "&eNhấp để xác nhận"
                    )
            );
            
            InteractiveItem confirmButton = new InteractiveItem(confirmItem, 49).onClick((player, clickType) -> {
                // Kiểm tra nếu người nhận không còn online
                if (!receiver.isOnline()) {
                    player.sendMessage(Chat.colorize("&8[&4&l✕&8] &cNgười chơi &f" + receiver.getName() + " &ckhông còn trực tuyến!"));
                    SoundManager.playSound(player, "ENTITY_VILLAGER_NO", 0.5f, 1.0f);
                    return;
                }
                
                // Thực hiện chuyển tài nguyên
                transferAllResources();
            });
            
            inventory.setItem(confirmButton.getSlot(), confirmButton);
        }
        
        // Thêm nút hủy
        ItemStack cancelItem = ItemManager.createItem(
                Material.BARRIER, 
                "&c&lHủy", 
                Arrays.asList(
                    "&7Quay lại kho cá nhân",
                    "&7",
                    "&eNhấp để hủy"
                )
        );
        
        InteractiveItem cancelButton = new InteractiveItem(cancelItem, 53).onClick((player, clickType) -> {
            player.closeInventory();
            // Sử dụng Bukkit scheduler với lambda thay vì BukkitRunnable
            Bukkit.getScheduler().runTask(Storage.getStorage(), () -> 
                player.openInventory(new PersonalStorage(player).getInventory())
            );
        });
        
        inventory.setItem(cancelButton.getSlot(), cancelButton);
    }
    
    /**
     * Kiểm tra người chơi có tồn tại trong database không
     * @param playerName Tên người chơi cần kiểm tra
     * @return true nếu người chơi tồn tại, false nếu không
     */
    private boolean checkPlayerExists(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return false;
        }
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            conn = Storage.db.getConnection();
            if (conn == null) {
                Storage.getStorage().getLogger().warning("Không thể kết nối đến cơ sở dữ liệu để kiểm tra người chơi.");
                return false;
            }
            
            // Tăng timeout cho SQLite để tránh SQLITE_BUSY
            try {
                conn.createStatement().execute("PRAGMA busy_timeout = 30000");
            } catch (Exception e) {
                // Bỏ qua nếu không hỗ trợ
            }
            
            // Kiểm tra người chơi tồn tại
            ps = conn.prepareStatement("SELECT COUNT(*) FROM " + Storage.db.table + " WHERE player = ?");
            ps.setQueryTimeout(10); // Timeout 10 giây cho query
            ps.setString(1, playerName);
            rs = ps.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            
            return false;
        } catch (SQLException e) {
            Storage.getStorage().getLogger().warning("Lỗi khi kiểm tra người chơi trong database: " + e.getMessage());
            return false;
        } finally {
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
                if (conn != null) Storage.db.returnConnection(conn);
            } catch (SQLException e) {
                Storage.getStorage().getLogger().warning("Lỗi khi đóng kết nối database: " + e.getMessage());
            }
        }
    }
    
    /**
     * Xử lý chuyển tất cả tài nguyên cho người chơi
     */
    private void transferAllResources() {
        // Kiểm tra xem receiver có tồn tại và online không
        Objects.requireNonNull(sender, "Người gửi không thể null");
        if (receiver == null || !receiver.isOnline()) {
            sender.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.action.transfer.player_not_online"))
                    .replace("#player#", receiver != null ? receiver.getName() : "người chơi")));
            try {
                String failSoundConfig = File.getConfig().getString("effects.transfer_fail.sound", "ENTITY_VILLAGER_NO:1.0:1.0");
                if (failSoundConfig != null && !failSoundConfig.isEmpty()) {
                    String[] parts = failSoundConfig.split(":");
                    org.bukkit.Sound sound = org.bukkit.Sound.valueOf(parts[0]);
                    float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                    float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                    sender.playSound(sender.getLocation(), sound, volume, pitch);
                }
            } catch (Exception e) {
                // Bỏ qua nếu âm thanh không hỗ trợ
            }
            return;
        }
        
        // Kiểm tra xem người nhận có tồn tại trong database không
        if (!checkPlayerExists(receiver.getName())) {
            sender.sendMessage(Chat.colorize("&8[&c&l✕&8] &cKhông thể chuyển tài nguyên: Người chơi &f" + receiver.getName() + " &cchưa từng tham gia máy chủ!"));
            try {
                String failSoundConfig = File.getConfig().getString("effects.transfer_fail.sound", "ENTITY_VILLAGER_NO:1.0:1.0");
                if (failSoundConfig != null && !failSoundConfig.isEmpty()) {
                    String[] parts = failSoundConfig.split(":");
                    org.bukkit.Sound sound = org.bukkit.Sound.valueOf(parts[0]);
                    float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                    float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                    sender.playSound(sender.getLocation(), sound, volume, pitch);
                }
            } catch (Exception e) {
                // Bỏ qua nếu âm thanh không hỗ trợ
            }
            return;
        }
        
        // Danh sách các tài nguyên đã chọn
        List<String> selectedResources = new ArrayList<>();
        
        // Duyệt qua từng tài nguyên đã chọn
        for (Map.Entry<String, Integer> entry : transferItems.entrySet()) {
            if (entry.getValue() > 0) {
                selectedResources.add(entry.getKey());
            }
        }
        
        if (selectedResources.isEmpty()) {
            sender.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.action.transfer.no_resource_selected",
                    "&8[&c&l✕&8] &cBạn chưa chọn tài nguyên nào để chuyển"))));
            try {
                String failSoundConfig = File.getConfig().getString("effects.transfer_fail.sound", "ENTITY_VILLAGER_NO:1.0:1.0");
                if (failSoundConfig != null && !failSoundConfig.isEmpty()) {
                    String[] parts = failSoundConfig.split(":");
                    org.bukkit.Sound sound = org.bukkit.Sound.valueOf(parts[0]);
                    float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                    float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                    sender.playSound(sender.getLocation(), sound, volume, pitch);
                }
            } catch (Exception e) {
                // Bỏ qua nếu âm thanh không hỗ trợ
            }
            return;
        }
        
        // Xử lý chuyển tài nguyên với synchronized để tránh race condition
        boolean anySuccess = false;
        int totalTransferred = 0;
        
        // Đọc phần trăm chuyển tài nguyên từ cấu hình
        int transferPercentage = File.getConfig().getInt("settings.transfer_percentage", 25);
        
        // Thông báo về phần trăm chuyển tài nguyên
        String transferLimitMessage = Objects.requireNonNull(File.getMessage().getString("user.action.transfer.transfer_limit", 
                "&8[&e&l!&8] &eGiới hạn chuyển: &a#percentage#% &etài nguyên mỗi lần để tránh lỡ tay"))
                .replace("#percentage#", String.valueOf(transferPercentage));
        sender.sendMessage(Chat.colorize(transferLimitMessage));
        
        // Danh sách tài nguyên chuyển thành công
        Map<String, Integer> transferDetails = new HashMap<>();
        
        // Sử dụng synchronized để đảm bảo các giao dịch không bị xung đột
        synchronized (receiver.getName().intern()) {
            // Chuyển từng tài nguyên đã chọn
            for (String material : selectedResources) {
                int amount = transferItems.get(material);
                
                if (amount <= 0) {
                    continue;
                }
                
                String materialName = File.getConfig().getString("items." + material, material.split(";")[0]);
                
                // Kiểm tra số lượng người gửi có
                int currentAmount = MineManager.getPlayerBlock(sender, material);
                if (currentAmount < amount) {
                    amount = currentAmount; // Chỉ chuyển số lượng hiện có
                    if (amount <= 0) {
                        continue;
                    }
                }
                
                // Kiểm tra giới hạn kho của người nhận
                int receiverAmount = MineManager.getPlayerBlock(receiver, material);
                int maxStorage = MineManager.getMaxBlock(receiver);
                
                // Kiểm tra xem kho của người nhận có đủ chỗ không
                if (receiverAmount >= maxStorage) {
                    // Bỏ qua tài nguyên này
                    continue;
                }
                
                // Kiểm tra không gian còn lại của người nhận
                int availableSpace = maxStorage - receiverAmount;
                
                // Nếu không đủ không gian, chỉ chuyển số lượng có thể
                if (amount > availableSpace) {
                    amount = availableSpace;
                }
                
                // Xử lý chuyển tài nguyên
                if (amount > 0) {
                    try {
                        // Xóa tài nguyên từ người gửi
                        MineManager.removeBlockAmount(sender, material, amount);
                        
                        // Thêm tài nguyên cho người nhận
                        MineManager.addBlockAmount(receiver, material, amount);
                        
                        // Ghi lại lịch sử giao dịch
                        com.hongminh54.storage.Utils.TransferManager.recordTransfer(sender, receiver, material, amount);
                        
                        // Cập nhật tổng và danh sách chi tiết
                        anySuccess = true;
                        totalTransferred += amount;
                        transferDetails.put(materialName, amount);
                    } catch (Exception e) {
                        // Ghi log lỗi nhưng tiếp tục với tài nguyên khác
                        Storage.getStorage().getLogger().warning("Lỗi khi chuyển tài nguyên " + material + ": " + e.getMessage());
                    }
                }
            }
        }
        
        // Thông báo kết quả
        if (anySuccess) {
            // Thông báo thành công cho người gửi
            StringBuilder detailMsg = new StringBuilder();
            for (Map.Entry<String, Integer> entry : transferDetails.entrySet()) {
                detailMsg.append("\n&7- &f").append(entry.getValue()).append(" &a").append(entry.getKey());
            }
            
            String successMsg = File.getMessage().getString("user.action.transfer.multi_success", 
                    "&8[&a&l✓&8] &aĐã chuyển các tài nguyên sau cho &e#player#:#details#");
                    
            String formattedSuccessMsg = successMsg
                    .replace("#player#", receiver.getName())
                    .replace("#details#", detailMsg.toString())
                    .replace("#total_amount#", String.valueOf(totalTransferred))
                    .replace("#count#", String.valueOf(transferDetails.size()));
                    
            sender.sendMessage(Chat.colorize(formattedSuccessMsg));
            
            // Thông báo cho người nhận
            String receivedMsg = File.getMessage().getString("user.action.transfer.multi_receive", 
                    "&8[&a&l✓&8] &aBạn nhận được các tài nguyên sau từ &e#player#:#details#");
                    
            String formattedReceivedMsg = receivedMsg
                    .replace("#player#", sender.getName())
                    .replace("#details#", detailMsg.toString())
                    .replace("#total_amount#", String.valueOf(totalTransferred))
                    .replace("#count#", String.valueOf(transferDetails.size()));
                    
            receiver.sendMessage(Chat.colorize(formattedReceivedMsg));
            
            // Đóng giao diện
            sender.closeInventory();
            
            // Phát hiệu ứng thành công
            playMultiTransferEffects(sender, receiver);
        } else {
            // Thông báo thất bại nếu không chuyển được tài nguyên nào
            sender.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.action.transfer.failed", 
                    "&8[&c&l✕&8] &cKhông thể chuyển tài nguyên: &f#reason#"))
                    .replace("#reason#", "Kho của người nhận đã đầy hoặc bạn không có tài nguyên nào để chuyển")));
            
            // Phát âm thanh thất bại
            try {
                String failSoundConfig = File.getConfig().getString("effects.transfer_fail.sound", "ENTITY_VILLAGER_NO:1.0:1.0");
                if (failSoundConfig != null && !failSoundConfig.isEmpty()) {
                    String[] parts = failSoundConfig.split(":");
                    org.bukkit.Sound sound = org.bukkit.Sound.valueOf(parts[0]);
                    float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                    float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                    sender.playSound(sender.getLocation(), sound, volume, pitch);
                }
            } catch (Exception e) {
                // Bỏ qua nếu âm thanh không hỗ trợ
            }
        }
    }
    
    /**
     * Phát hiệu ứng khi chuyển nhiều tài nguyên
     */
    private void playMultiTransferEffects(Player sender, Player receiver) {
        if (sender == null || receiver == null || !sender.isOnline() || !receiver.isOnline()) {
            return;
        }
        
        try {
            // Đọc cấu hình hiệu ứng
            int maxParticleCount = File.getConfig().getInt("settings.max_particle_count", 50);
            
            // Phát âm thanh cho người gửi
            String senderSoundConfig = File.getConfig().getString("effects.transfer_success.sender_sound", "ENTITY_PLAYER_LEVELUP:0.5:1.2");
            if (senderSoundConfig != null && !senderSoundConfig.isEmpty()) {
                try {
                    String[] parts = senderSoundConfig.split(":");
                    org.bukkit.Sound sound = org.bukkit.Sound.valueOf(parts[0]);
                    float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 0.5f;
                    float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.2f;
                    sender.playSound(sender.getLocation(), sound, volume, pitch);
                } catch (Exception e) {
                    // Bỏ qua nếu âm thanh không hỗ trợ
                }
            }
            
            // Phát âm thanh cho người nhận
            String receiverSoundConfig = File.getConfig().getString("effects.transfer_success.receiver_sound", "ENTITY_EXPERIENCE_ORB_PICKUP:0.5:1.0");
            if (receiverSoundConfig != null && !receiverSoundConfig.isEmpty()) {
                try {
                    String[] parts = receiverSoundConfig.split(":");
                    org.bukkit.Sound sound = org.bukkit.Sound.valueOf(parts[0]);
                    float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 0.5f;
                    float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                    receiver.playSound(receiver.getLocation(), sound, volume, pitch);
                } catch (Exception e) {
                    // Bỏ qua nếu âm thanh không hỗ trợ
                }
            }
            
            // Hiệu ứng hạt cho người gửi
            applyParticleEffect(sender, "effects.transfer_success.sender_particle", maxParticleCount);
            
            // Hiệu ứng hạt cho người nhận
            applyParticleEffect(receiver, "effects.transfer_success.receiver_particle", maxParticleCount);
            
            // Hiệu ứng bổ sung cho giao dịch lớn (sử dụng giá trị cố định)
            int particleBoostThreshold = File.getConfig().getInt("settings.large_transfer_threshold", 100);
            boolean isLargeTransfer = false;
            
            // Tính tổng số tài nguyên chuyển
            int total = 0;
            for (Integer value : transferItems.values()) {
                total += value;
            }
            
            if (total > particleBoostThreshold) {
                isLargeTransfer = true;
                String largeSenderParticleConfig = File.getConfig().getString("effects.large_transfer.sender_particle", "SPELL_WITCH:0.2:0.2:0.2:0.05");
                String largeReceiverParticleConfig = File.getConfig().getString("effects.large_transfer.receiver_particle", "TOTEM:0.5:0.5:0.5:0.1");
                
                if (largeSenderParticleConfig != null && !largeSenderParticleConfig.isEmpty()) {
                    try {
                        String[] parts = largeSenderParticleConfig.split(":");
                        org.bukkit.Particle particle = org.bukkit.Particle.valueOf(parts[0]);
                        double offsetX = parts.length > 1 ? Double.parseDouble(parts[1]) : 0.2;
                        double offsetY = parts.length > 2 ? Double.parseDouble(parts[2]) : 0.2;
                        double offsetZ = parts.length > 3 ? Double.parseDouble(parts[3]) : 0.2;
                        double speed = parts.length > 4 ? Double.parseDouble(parts[4]) : 0.05;
                        
                        sender.getWorld().spawnParticle(particle, sender.getLocation().add(0, 1.5, 0),
                                Math.min(20, maxParticleCount), offsetX, offsetY, offsetZ, speed);
                    } catch (Exception e) {
                        // Bỏ qua nếu hiệu ứng không hỗ trợ
                    }
                }
                
                if (largeReceiverParticleConfig != null && !largeReceiverParticleConfig.isEmpty()) {
                    try {
                        String[] parts = largeReceiverParticleConfig.split(":");
                        org.bukkit.Particle particle = org.bukkit.Particle.valueOf(parts[0]);
                        double offsetX = parts.length > 1 ? Double.parseDouble(parts[1]) : 0.5;
                        double offsetY = parts.length > 2 ? Double.parseDouble(parts[2]) : 0.5;
                        double offsetZ = parts.length > 3 ? Double.parseDouble(parts[3]) : 0.5;
                        double speed = parts.length > 4 ? Double.parseDouble(parts[4]) : 0.1;
                        
                        receiver.getWorld().spawnParticle(particle, receiver.getLocation().add(0, 1.5, 0),
                                Math.min(20, maxParticleCount), offsetX, offsetY, offsetZ, speed);
                    } catch (Exception e) {
                        // Bỏ qua nếu hiệu ứng không hỗ trợ
                    }
                }
            }
        } catch (Exception e) {
            // Bỏ qua lỗi tổng thể
            Storage.getStorage().getLogger().warning("Lỗi khi phát hiệu ứng chuyển tài nguyên: " + e.getMessage());
        }
    }
    
    /**
     * Áp dụng hiệu ứng hạt cho người chơi
     * @param player Người chơi nhận hiệu ứng
     * @param configPath Đường dẫn đến cấu hình hiệu ứng trong file config
     * @param maxParticleCount Số lượng hạt tối đa
     */
    private void applyParticleEffect(Player player, String configPath, int maxParticleCount) {
        if (player == null || !player.isOnline() || maxParticleCount <= 0) {
            return;
        }
        
        try {
            String particleConfig = File.getConfig().getString(configPath, "VILLAGER_HAPPY:0.5:0.5:0.5:0.1:20");
            if (particleConfig != null && !particleConfig.isEmpty()) {
                String[] parts = particleConfig.split(":");
                if (parts.length < 1) return;
                
                org.bukkit.Particle particle = org.bukkit.Particle.valueOf(parts[0]);
                double offsetX = parts.length > 1 ? Double.parseDouble(parts[1]) : 0.5;
                double offsetY = parts.length > 2 ? Double.parseDouble(parts[2]) : 0.5;
                double offsetZ = parts.length > 3 ? Double.parseDouble(parts[3]) : 0.5;
                double speed = parts.length > 4 ? Double.parseDouble(parts[4]) : 0.1;
                int count = parts.length > 5 ? Math.min(Integer.parseInt(parts[5]), maxParticleCount) : Math.min(20, maxParticleCount);
                
                player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0), 
                        count, offsetX, offsetY, offsetZ, speed);
            }
        } catch (Exception e) {
            // Bỏ qua nếu hiệu ứng hạt không hỗ trợ
        }
    }
    
    /**
     * Lấy người chơi gửi
     * @return Người gửi
     */
    public Player getSender() {
        return sender;
    }
    
    /**
     * Lấy người chơi nhận
     * @return Người nhận
     */
    public Player getReceiver() {
        return receiver;
    }
    
    /**
     * Lấy danh sách tài nguyên sẽ chuyển
     * @return Danh sách tài nguyên
     */
    public Map<String, Integer> getTransferItems() {
        return transferItems;
    }

    /**
     * Xử lý sự kiện click vào inventory
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Kiểm tra nếu GUI này là một phần của view của người chơi
        if (event.getView().getTopInventory() != inventory) {
            return;
        }
        
        // Hủy tất cả các sự kiện click để ngăn người chơi lấy item 
        // Bao gồm tất cả các slot trong GUI, kể cả bottom inventory
        event.setCancelled(true);
        
        // Nếu không phải click vào inventory này hoặc không có item thì bỏ qua
        if (event.getCurrentItem() == null || event.getRawSlot() >= inventory.getSize() || 
            event.getRawSlot() < 0) {
            return;
        }
        
        // Xử lý các tương tác còn lại, như click vào các nút chức năng
        Player player = (Player) event.getWhoClicked();
        ClickType clickType = event.getClick();
        int slot = event.getRawSlot();
        
        // Nút xác nhận ở slot 49
        if (slot == 49) {
            // Kiểm tra nếu người nhận không còn online
            if (!receiver.isOnline()) {
                player.sendMessage(Chat.colorize("&8[&4&l✕&8] &cNgười chơi &f" + receiver.getName() + " &ckhông còn trực tuyến!"));
                SoundManager.playSound(player, "ENTITY_VILLAGER_NO", 0.5f, 1.0f);
                return;
            }
            
            // Thực hiện chuyển tài nguyên
            transferAllResources();
        }
        
        // Xử lý các nút tương tác khác nếu cần
    }
    
    /**
     * Xử lý sự kiện đóng inventory
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            // Hủy đăng ký listener khi đóng giao diện - Sử dụng Bukkit scheduler với lambda thay vì BukkitRunnable
            Bukkit.getScheduler().runTaskLater(Storage.getStorage(), () -> {
                unregisterListener();
                stopRefreshTask(); // Dừng task cập nhật khi đóng GUI
            }, 1L);
        }
    }
    
    /**
     * Xử lý sự kiện kéo vật phẩm trong inventory
     * Ngăn người chơi kéo vật phẩm trong GUI
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().equals(inventory)) {
            // Hủy tất cả các sự kiện kéo vật phẩm trong GUI này
            event.setCancelled(true);
        }
    }
} 