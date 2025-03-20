package com.hongminh54.storage.GUI;

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import com.hongminh54.storage.GUI.manager.IGUI;
import com.hongminh54.storage.GUI.manager.InteractiveItem;
import com.hongminh54.storage.Manager.ItemManager;
import com.hongminh54.storage.Manager.MineManager;
import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.Chat;
import com.hongminh54.storage.Utils.File;
import com.hongminh54.storage.Utils.GUIText;

/**
 * Giao diện chuyển tài nguyên giữa người chơi
 */
public class TransferGUI implements IGUI, Listener {

    private final Player sender;
    private final Player receiver;
    private final String material;
    private final FileConfiguration config;
    private Inventory inventory;
    private boolean listenerRegistered = false;

    /**
     * Khởi tạo giao diện chuyển tài nguyên
     * @param sender Người gửi tài nguyên
     * @param receiver Người nhận tài nguyên
     * @param material Loại tài nguyên
     */
    public TransferGUI(Player sender, Player receiver, String material) {
        this.sender = Objects.requireNonNull(sender, "Người gửi không thể là null");
        this.receiver = Objects.requireNonNull(receiver, "Người nhận không thể là null");
        this.material = Objects.requireNonNull(material, "Vật liệu không thể là null");
        this.config = File.getConfig(); // Sử dụng config chính của plugin
        
        // Đăng ký Listener khi tạo GUI
        registerListener();
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

    @NotNull
    @Override
    public Inventory getInventory() {
        // Tạo tiêu đề giao diện
        String title = "&8Chuyển tài nguyên cho &a" + receiver.getName();
        inventory = Bukkit.createInventory(sender, 45, GUIText.format(title));
        
        // Thêm viền trang trí
        addBorder();
        
        // Lấy thông tin số lượng tài nguyên hiện có
        int currentAmount = MineManager.getPlayerBlock(sender, material);
        String materialName = Objects.requireNonNull(File.getConfig().getString("items." + material, material.split(";")[0]));
        
        // Kiểm tra nếu không có tài nguyên
        if (currentAmount <= 0) {
            sender.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.action.transfer.not_enough"))
                    .replace("#material#", materialName)
                    .replace("#amount#", "0")));
            
            // Phát âm thanh thất bại
            try {
                String failSoundConfig = File.getConfig().getString("effects.transfer_fail.sound", "ENTITY_VILLAGER_NO:1.0:1.0");
                String[] soundParts = failSoundConfig.split(":");
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundParts[0]);
                float volume = soundParts.length > 1 ? Float.parseFloat(soundParts[1]) : 1.0f;
                float pitch = soundParts.length > 2 ? Float.parseFloat(soundParts[2]) : 1.0f;
                
                sender.playSound(sender.getLocation(), sound, volume, pitch);
            } catch (Exception e) {
                // Phát âm thanh cảnh báo
                try {
                    sender.playSound(sender.getLocation(), org.bukkit.Sound.valueOf("NOTE_BASS"), 0.5f, 0.8f);
                } catch (Exception ex) {
                    // Bỏ qua nếu không hỗ trợ
                }
            }
            
            // Đóng giao diện và mở lại giao diện kho cá nhân sau 1 tick
            new BukkitRunnable() {
                @Override
                public void run() {
                    sender.closeInventory();
                    // Có thể mở lại giao diện kho cá nhân nếu cần
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            sender.openInventory(new PersonalStorage(sender).getInventory());
                        }
                    }.runTask(Storage.getStorage());
                }
            }.runTask(Storage.getStorage());
            
            // Trả về giao diện trống, người chơi sẽ không thấy nó vì closeInventory đã được gọi
            return inventory;
        }

        // Thêm thông tin người chơi
        addPlayerInfo(materialName);

        // Thêm các nút số lượng
        // Hàng đầu tiên: 1, 5, 10 tài nguyên
        addQuantityButton(inventory, 11, 1, materialName);
        addQuantityButton(inventory, 13, 5, materialName);
        addQuantityButton(inventory, 15, 10, materialName);
        
        // Hàng thứ hai: 32, 64 tài nguyên
        addQuantityButton(inventory, 20, 32, materialName);
        addQuantityButton(inventory, 24, 64, materialName);
        
        // Hàng cuối: Tùy chỉnh và chuyển tất cả
        addCustomQuantityButton(inventory, 29, materialName);
        addAllQuantityButton(inventory, 33, currentAmount, materialName);
        
        // Thêm nút xác nhận và hủy
        addControlButtons(materialName);
        
        return inventory;
    }
    
    /**
     * Thêm viền trang trí cho GUI
     */
    private void addBorder() {
        // Sử dụng Material.valueOf để tương thích với 1.12.2
        ItemStack borderItem;
        try {
            borderItem = new ItemStack(Material.valueOf("STAINED_GLASS_PANE"), 1, (short) 7);
            ItemMeta meta = borderItem.getItemMeta();
            meta.setDisplayName(Chat.colorize("&r"));
            borderItem.setItemMeta(meta);
        } catch (Exception e) {
            // Fallback nếu có lỗi
            borderItem = ItemManager.createItem(
                    Material.STONE, 
                    "&r", 
                    Arrays.asList("")
            );
        }
        
        // Viền trên (0-8)
        for (int i = 0; i <= 8; i++) {
            inventory.setItem(i, borderItem);
        }
        
        // Viền dưới (36-44)
        for (int i = 36; i <= 44; i++) {
            inventory.setItem(i, borderItem);
        }
        
        // Viền bên trái (9, 18, 27)
        inventory.setItem(9, borderItem);
        inventory.setItem(18, borderItem);
        inventory.setItem(27, borderItem);
        
        // Viền bên phải (17, 26, 35)
        inventory.setItem(17, borderItem);
        inventory.setItem(26, borderItem);
        inventory.setItem(35, borderItem);
    }
    
    /**
     * Thêm thông tin người chơi và tài nguyên
     * @param materialName Tên tài nguyên
     */
    private void addPlayerInfo(String materialName) {
        // Thêm thông tin người chơi nhận, sử dụng EMERALD thay vì đầu người chơi
        ItemStack receiverInfo = ItemManager.createItem(
            Material.EMERALD, 
            "&a&l" + receiver.getName(), 
            Arrays.asList(
                "&7Người nhận: &a" + receiver.getName(),
                "&7Tài nguyên: &b" + materialName,
                "&7",
                "&eLựa chọn số lượng muốn chuyển"
            )
        );
        
        inventory.setItem(4, receiverInfo);
        
        // Thông tin người chuyển và tài nguyên
        int currentAmount = MineManager.getPlayerBlock(sender, material);
        
        ItemStack senderInfo = ItemManager.createItem(
                Material.CHEST, 
                "&e&lSố lượng hiện có", 
                Arrays.asList(
                    "&7Bạn đang có: &a" + currentAmount + " " + materialName,
                    "&7",
                    "&eChọn số lượng muốn chuyển bên dưới"
                )
        );
        
        inventory.setItem(22, senderInfo);
    }
    
    /**
     * Thêm nút xác nhận và hủy
     * @param materialName Tên tài nguyên
     */
    private void addControlButtons(String materialName) {
        // Đọc phần trăm chuyển tài nguyên từ cấu hình
        final int transferPercentage = File.getConfig().getInt("settings.transfer_percentage", 25);
        final int playerAmount = MineManager.getPlayerBlock(sender, material);
        final int defaultTransferAmount = (playerAmount < 4) ? playerAmount : Math.max(1, (playerAmount * transferPercentage) / 100);
        
        // Thêm nút xác nhận
        ItemStack confirmItem = ItemManager.createItem(
                Material.EMERALD_BLOCK, 
                "&a&lXác nhận chuyển", 
                Arrays.asList(
                    "&7Chuyển &f" + defaultTransferAmount + " &b" + materialName + " &7cho &a" + receiver.getName(),
                    "&7",
                    "&eNhấp để xác nhận"
                )
        );
        
        InteractiveItem confirmButton = new InteractiveItem(confirmItem, 40).onClick((player, clickType) -> {
            player.closeInventory();
            
            // Mở một giao diện xác nhận
            int currentAmount = MineManager.getPlayerBlock(player, material);
            if (currentAmount <= 0) {
                player.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.action.transfer.not_enough"))
                        .replace("#material#", materialName)
                        .replace("#amount#", "0")));
                try {
                    // Sử dụng âm thanh thất bại từ cấu hình
                    String failSoundConfig = File.getConfig().getString("effects.transfer_fail.sound", "ENTITY_VILLAGER_NO:1.0:1.0");
                    String[] soundParts = failSoundConfig.split(":");
                    org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundParts[0]);
                    float volume = soundParts.length > 1 ? Float.parseFloat(soundParts[1]) : 1.0f;
                    float pitch = soundParts.length > 2 ? Float.parseFloat(soundParts[2]) : 1.0f;
                    
                    player.playSound(player.getLocation(), sound, volume, pitch);
                } catch (Exception e) {
                    // Phát âm thanh cảnh báo
                    try {
                        player.playSound(player.getLocation(), org.bukkit.Sound.valueOf("NOTE_BASS"), 0.5f, 0.8f);
                    } catch (Exception ex) {
                        // Bỏ qua nếu không hỗ trợ
                    }
                }
                return;
            }
            
            // Người chơi không còn trực tuyến
            if (!receiver.isOnline()) {
                player.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.action.transfer.player_not_online"))
                        .replace("#player#", receiver.getName())));
                try {
                    // Sử dụng âm thanh thất bại từ cấu hình
                    String failSoundConfig = File.getConfig().getString("effects.transfer_fail.sound", "ENTITY_VILLAGER_NO:1.0:1.0");
                    String[] soundParts = failSoundConfig.split(":");
                    org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundParts[0]);
                    float volume = soundParts.length > 1 ? Float.parseFloat(soundParts[1]) : 1.0f;
                    float pitch = soundParts.length > 2 ? Float.parseFloat(soundParts[2]) : 1.0f;
                    
                    player.playSound(player.getLocation(), sound, volume, pitch);
                } catch (Exception e) {
                    // Phát âm thanh cảnh báo
                    try {
                        player.playSound(player.getLocation(), org.bukkit.Sound.valueOf("NOTE_BASS"), 0.5f, 0.8f);
                    } catch (Exception ex) {
                        // Bỏ qua nếu không hỗ trợ
                    }
                }
                return;
            }
            
            // Chuyển tối đa transferPercentage% tài nguyên cho mỗi lần xác nhận
            int transferAmount = (currentAmount < 4) ? currentAmount : Math.max(1, (currentAmount * transferPercentage) / 100);
            int percentage = (transferAmount * 100) / Math.max(1, currentAmount);
            
            // Giải thích lý do chỉ cho chuyển transferPercentage% mỗi lần
            String transferLimitMessage = Objects.requireNonNull(File.getMessage().getString("user.action.transfer.transfer_limit", 
                "&8[&e&l!&8] &eGiới hạn chuyển: &a" + transferPercentage + "% &etài nguyên mỗi lần để tránh lỡ tay"));
            
            if (currentAmount < 4) {
                player.sendMessage(Chat.colorize("&8[&e&l❖&8] &aĐang chuyển &f" + transferAmount + " &a" + materialName + 
                    " &fcho &e" + receiver.getName() + " &7(Số lượng ít nên chuyển hết)"));
            } else {
                player.sendMessage(Chat.colorize("&8[&e&l❖&8] &aĐang chuyển &f" + transferAmount + " &a" + materialName + 
                        " &7(" + percentage + "% tổng số) &fcho &e" + receiver.getName()));
                player.sendMessage(Chat.colorize(transferLimitMessage));
            }
                    
            transferResource(player, receiver, material, transferAmount);
        });
        
        inventory.setItem(confirmButton.getSlot(), confirmButton);
        
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
        
        InteractiveItem cancelButton = new InteractiveItem(cancelItem, 44).onClick((player, clickType) -> {
            player.closeInventory();
            new BukkitRunnable() {
                @Override
                public void run() {
                    player.openInventory(new PersonalStorage(player).getInventory());
                }
            }.runTask(Storage.getStorage());
        });
        
        inventory.setItem(cancelButton.getSlot(), cancelButton);
    }
    
    /**
     * Thêm nút số lượng cố định vào giao diện
     * @param inventory Giao diện
     * @param slot Vị trí
     * @param amount Số lượng
     * @param materialName Tên tài nguyên
     */
    private void addQuantityButton(Inventory inventory, int slot, int amount, String materialName) {
        ItemStack item = ItemManager.createItem(
                Material.PAPER, 
                "&a&l" + amount, 
                Arrays.asList(
                    "&7Chuyển &a" + amount + " " + materialName,
                    "&7cho &a" + receiver.getName(),
                    "&7",
                    "&eNhấp để chọn"
                )
        );
        
        InteractiveItem button = new InteractiveItem(item, slot).onClick((player, clickType) -> {
            int currentAmount = MineManager.getPlayerBlock(player, material);
            if (currentAmount < amount) {
                player.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.action.transfer.not_enough"))
                        .replace("#material#", materialName)
                        .replace("#amount#", String.valueOf(currentAmount))));
                return;
            }
            
            // Xử lý chuyển tài nguyên
            transferResource(player, receiver, material, amount);
        });
        
        inventory.setItem(button.getSlot(), button);
    }
    
    /**
     * Thêm nút số lượng tùy chỉnh vào giao diện
     * @param inventory Giao diện
     * @param slot Vị trí
     * @param materialName Tên tài nguyên
     */
    private void addCustomQuantityButton(Inventory inventory, int slot, String materialName) {
        ItemStack item = ItemManager.createItem(
                Material.NAME_TAG, 
                "&a&lTùy chỉnh", 
                Arrays.asList(
                    "&7Nhập số lượng tùy chỉnh",
                    "&7để chuyển cho &a" + receiver.getName(),
                    "&7",
                    "&eNhấp để chọn"
                )
        );
        
        InteractiveItem button = new InteractiveItem(item, slot).onClick((player, clickType) -> {
            player.closeInventory();
            player.sendMessage(Chat.colorize(
                Objects.requireNonNull(File.getMessage().getString("user.action.transfer.chat_number", "&8[&b&l❖&8] &aNhập số lượng muốn chuyển:"))));
            
            // Thêm người chơi vào danh sách chat
            SimpleEntry<Player, String> transferInfo = new SimpleEntry<>(receiver, material);
            com.hongminh54.storage.Listeners.Chat.chat_transfer.put(player, transferInfo);
        });
        
        inventory.setItem(button.getSlot(), button);
    }
    
    /**
     * Thêm nút chuyển tất cả vào giao diện
     * @param inventory Giao diện
     * @param slot Vị trí
     * @param currentAmount Số lượng hiện có
     * @param materialName Tên tài nguyên
     */
    private void addAllQuantityButton(Inventory inventory, int slot, int currentAmount, String materialName) {
        // Đọc phần trăm chuyển tài nguyên từ cấu hình
        int transferPercentage = File.getConfig().getInt("settings.transfer_percentage", 25);
        
        ItemStack item = ItemManager.createItem(
                Material.HOPPER, 
                "&a&lTất cả (" + currentAmount + ")", 
                Arrays.asList(
                    "&7Chuyển tất cả &a" + currentAmount + " " + materialName,
                    "&7cho &a" + receiver.getName(),
                    "&7",
                    "&cLưu ý: &7Sẽ chỉ chuyển &a" + transferPercentage + "% &7mỗi lần nhấp",
                    "&7để tránh việc lỡ tay chuyển tất cả",
                    "&7",
                    "&eNhấp để chọn"
                )
        );
        
        InteractiveItem button = new InteractiveItem(item, slot).onClick((player, clickType) -> {
            // Kiểm tra số lượng hiện có (lấy lại giá trị mới nhất)
            int amount = MineManager.getPlayerBlock(player, material);
            if (amount <= 0) {
                player.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.action.transfer.not_enough"))
                        .replace("#material#", materialName)
                        .replace("#amount#", "0")));
                try {
                    // Sử dụng âm thanh thất bại từ cấu hình
                    String failSoundConfig = File.getConfig().getString("effects.transfer_fail.sound", "ENTITY_VILLAGER_NO:1.0:1.0");
                    if (failSoundConfig != null && !failSoundConfig.isEmpty()) {
                        String[] parts = failSoundConfig.split(":");
                        org.bukkit.Sound sound = org.bukkit.Sound.valueOf(parts[0]);
                        float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                        float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                        player.playSound(player.getLocation(), sound, volume, pitch);
                    }
                } catch (Exception e) {
                    // Bỏ qua nếu âm thanh không hỗ trợ
                }
                return;
            }
            
            // Chỉ chuyển transferPercentage% số lượng khi nhấp nút "Tất cả" để tránh lỡ tay
            int transferAmount = Math.max(1, (amount * transferPercentage) / 100);
            
            // Thông báo lý do chỉ chuyển transferPercentage%
            if (amount >= 4) {
                String transferLimitMessage = Objects.requireNonNull(File.getMessage().getString("user.action.transfer.transfer_limit", 
                    "&8[&e&l!&8] &eGiới hạn chuyển: &a" + transferPercentage + "% &etài nguyên mỗi lần để tránh lỡ tay"));
                player.sendMessage(Chat.colorize(transferLimitMessage.replace("#percentage#", String.valueOf(transferPercentage))));
            }
            
            // Xử lý chuyển tài nguyên
            transferResource(player, receiver, material, transferAmount);
        });
        
        inventory.setItem(button.getSlot(), button);
    }
    
    /**
     * Xử lý chuyển tài nguyên giữa người chơi
     * @param sender Người gửi
     * @param receiver Người nhận
     * @param material Loại tài nguyên
     * @param amount Số lượng
     */
    public void transferResource(Player sender, Player receiver, String material, int amount) {
        if (sender == null || !sender.isOnline() || receiver == null || !receiver.isOnline()) {
            if (sender != null && sender.isOnline()) {
                sender.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.action.transfer.player_not_online"))
                        .replace("#player#", receiver != null ? receiver.getName() : "người chơi")));
            }
            return;
        }
        
        // Kiểm tra xem người chơi có đủ tài nguyên không
        int senderAmount = MineManager.getPlayerBlock(sender, material);
        String materialName = File.getConfig().getString("items." + material, material != null ? material.split(";")[0] : "unknown");
        if (materialName == null) {
            materialName = "unknown";
        }
        
        if (senderAmount < amount) {
            sender.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.action.transfer.not_enough"))
                    .replace("#material#", materialName)
                    .replace("#amount#", String.valueOf(senderAmount))));
            
            // Phát âm thanh thất bại
            try {
                // Sử dụng âm thanh thất bại từ cấu hình
                String failSoundConfig = File.getConfig().getString("effects.transfer_fail.sound", "ENTITY_VILLAGER_NO:1.0:1.0");
                String[] soundParts = failSoundConfig.split(":");
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundParts[0]);
                float volume = soundParts.length > 1 ? Float.parseFloat(soundParts[1]) : 1.0f;
                float pitch = soundParts.length > 2 ? Float.parseFloat(soundParts[2]) : 1.0f;
                
                sender.playSound(sender.getLocation(), sound, volume, pitch);
            } catch (Exception e) {
                // Phát âm thanh cảnh báo
                try {
                    sender.playSound(sender.getLocation(), org.bukkit.Sound.valueOf("NOTE_BASS"), 0.5f, 0.8f);
                } catch (Exception ex) {
                    // Bỏ qua nếu không hỗ trợ
                }
            }
            return;
        }
        
        // Kiểm tra giới hạn kho của người nhận
        int receiverAmount = MineManager.getPlayerBlock(receiver, material);
        int maxStorage = MineManager.getMaxBlock(receiver);
        
        // Kiểm tra xem kho của người nhận có đủ chỗ không
        if (receiverAmount >= maxStorage) {
            sender.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.action.transfer.receiver_full"))
                    .replace("#player#", receiver.getName())));
            
            // Phát âm thanh thất bại
            try {
                // Sử dụng âm thanh thất bại từ cấu hình
                String failSoundConfig = File.getConfig().getString("effects.transfer_fail.sound", "ENTITY_VILLAGER_NO:1.0:1.0");
                String[] soundParts = failSoundConfig.split(":");
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundParts[0]);
                float volume = soundParts.length > 1 ? Float.parseFloat(soundParts[1]) : 1.0f;
                float pitch = soundParts.length > 2 ? Float.parseFloat(soundParts[2]) : 1.0f;
                
                sender.playSound(sender.getLocation(), sound, volume, pitch);
            } catch (Exception e) {
                // Phát âm thanh cảnh báo
                try {
                    sender.playSound(sender.getLocation(), org.bukkit.Sound.valueOf("NOTE_BASS"), 0.5f, 0.8f);
                } catch (Exception ex) {
                    // Bỏ qua nếu không hỗ trợ
                }
            }
            return;
        }
        
        // Kiểm tra không gian còn lại của người nhận
        int availableSpace = maxStorage - receiverAmount;
        int transferAmount = amount;
        
        // Nếu không đủ không gian, chỉ chuyển số lượng có thể
        if (amount > availableSpace) {
            transferAmount = availableSpace;
            
            // Thông báo cho người gửi về việc chỉ chuyển được một phần
            sender.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.action.transfer.partial_success",
                    "&8[&e&l!&8] &eChỉ có thể chuyển &f" + transferAmount + " &e#material# &7(giới hạn kho của người nhận)"))
                    .replace("#amount#", String.valueOf(transferAmount))
                    .replace("#material#", materialName)));
        }
        
        // Xóa tài nguyên từ người gửi
        MineManager.removeBlockAmount(sender, material, transferAmount);
        
        // Thêm tài nguyên cho người nhận
        MineManager.addBlockAmount(receiver, material, transferAmount);
        
        // Ghi lại lịch sử giao dịch
        com.hongminh54.storage.Utils.TransferManager.recordTransfer(sender, receiver, material, transferAmount);
        
        // Thông báo thành công cho người gửi
        sender.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.action.transfer.transfer_item"))
                .replace("#amount#", String.valueOf(transferAmount))
                .replace("#material#", materialName)
                .replace("#player#", receiver.getName())));
        
        // Thông báo cho người nhận
        receiver.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.action.transfer.receive_item"))
                .replace("#amount#", String.valueOf(transferAmount))
                .replace("#material#", materialName)
                .replace("#player#", sender.getName())));
        
        // Đóng giao diện sau khi chuyển
        sender.closeInventory();
        
        // Phát hiệu ứng và âm thanh
        applyTransferEffects(sender, receiver);
    }
    
    /**
     * Áp dụng hiệu ứng sau khi chuyển tài nguyên
     * 
     * @param sender Người gửi
     * @param receiver Người nhận
     */
    private void applyTransferEffects(Player sender, Player receiver) {
        // Đọc tham số hiệu ứng từ cấu hình
        int maxParticleCount = File.getConfig().getInt("settings.max_particle_count", 15);
        
        // Hiệu ứng bên người gửi
        try {
            // Hiệu ứng âm thanh
            String senderSoundConfig = File.getConfig().getString("effects.transfer_success.sender_sound", "ENTITY_PLAYER_LEVELUP:0.5:1.2");
            if (senderSoundConfig != null && !senderSoundConfig.isEmpty()) {
                String[] parts = senderSoundConfig.split(":");
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(parts[0]);
                float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 0.5f;
                float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.2f;
                sender.playSound(sender.getLocation(), sound, volume, pitch);
            }
            
            // Hiệu ứng hạt - giảm số lượng
            String senderParticleConfig = File.getConfig().getString("effects.transfer_success.sender_particle", "VILLAGER_HAPPY:0.5:0.5:0.5:0.1:10");
            if (senderParticleConfig != null && !senderParticleConfig.isEmpty() && maxParticleCount > 0) {
                String[] parts = senderParticleConfig.split(":");
                org.bukkit.Particle particle = org.bukkit.Particle.valueOf(parts[0]);
                double offsetX = parts.length > 1 ? Double.parseDouble(parts[1]) : 0.5;
                double offsetY = parts.length > 2 ? Double.parseDouble(parts[2]) : 0.5;
                double offsetZ = parts.length > 3 ? Double.parseDouble(parts[3]) : 0.5;
                double speed = parts.length > 4 ? Double.parseDouble(parts[4]) : 0.1;
                int count = parts.length > 5 ? Integer.parseInt(parts[5]) : 10;
                
                // Giới hạn số lượng hạt chặt chẽ hơn
                count = Math.min(count, maxParticleCount);
                count = Math.min(count, 15); // Hard limit ở 15 hạt
                
                sender.getWorld().spawnParticle(particle, sender.getLocation().add(0, 1, 0), 
                        count, offsetX, offsetY, offsetZ, speed);
            }
        } catch (Exception e) {
            // Bỏ qua nếu hiệu ứng không hỗ trợ
        }
        
        // Hiệu ứng bên người nhận
        if (receiver == null || !receiver.isOnline()) return;
        
        try {
            // Hiệu ứng âm thanh
            String receiverSoundConfig = File.getConfig().getString("effects.transfer_success.receiver_sound", "ENTITY_EXPERIENCE_ORB_PICKUP:0.5:1.0");
            if (receiverSoundConfig != null && !receiverSoundConfig.isEmpty()) {
                String[] parts = receiverSoundConfig.split(":");
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(parts[0]);
                float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 0.5f;
                float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                receiver.playSound(receiver.getLocation(), sound, volume, pitch);
            }
            
            // Hiệu ứng hạt - giảm số lượng
            String receiverParticleConfig = File.getConfig().getString("effects.transfer_success.receiver_particle", "VILLAGER_HAPPY:0.5:0.5:0.5:0.1:10");
            if (receiverParticleConfig != null && !receiverParticleConfig.isEmpty() && maxParticleCount > 0) {
                String[] parts = receiverParticleConfig.split(":");
                org.bukkit.Particle particle = org.bukkit.Particle.valueOf(parts[0]);
                double offsetX = parts.length > 1 ? Double.parseDouble(parts[1]) : 0.5;
                double offsetY = parts.length > 2 ? Double.parseDouble(parts[2]) : 0.5;
                double offsetZ = parts.length > 3 ? Double.parseDouble(parts[3]) : 0.5;
                double speed = parts.length > 4 ? Double.parseDouble(parts[4]) : 0.1;
                int count = parts.length > 5 ? Integer.parseInt(parts[5]) : 10;
                
                // Giới hạn số lượng hạt chặt chẽ hơn
                count = Math.min(count, maxParticleCount);
                count = Math.min(count, 15); // Hard limit ở 15 hạt
                
                receiver.getWorld().spawnParticle(particle, receiver.getLocation().add(0, 1, 0), 
                        count, offsetX, offsetY, offsetZ, speed);
            }
        } catch (Exception e) {
            // Bỏ qua nếu hiệu ứng không hỗ trợ
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
     * Lấy loại tài nguyên
     * @return Loại tài nguyên
     */
    public String getMaterial() {
        return material;
    }
    
    /**
     * Lấy cấu hình
     * @return Cấu hình
     */
    public FileConfiguration getConfig() {
        return config;
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
        // Quan trọng: Ngăn chặn người chơi click vào bất kỳ slot nào trong view
        event.setCancelled(true);
        
        // Nếu không phải click vào inventory này hoặc không có item thì bỏ qua
        if (event.getCurrentItem() == null || event.getRawSlot() >= inventory.getSize() || 
            event.getRawSlot() < 0) {
            return;
        }
        
        // Xử lý các click vào item tương tác trong inventory
        // Các nút tương tác đã được tạo với InteractiveItem và xử lý riêng
    }
    
    /**
     * Xử lý sự kiện đóng inventory
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            // Hủy đăng ký listener khi đóng giao diện
            new BukkitRunnable() {
                @Override
                public void run() {
                    unregisterListener();
                }
            }.runTaskLater(Storage.getStorage(), 1L);
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