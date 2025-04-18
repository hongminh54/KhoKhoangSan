package com.hongminh54.storage.GUI;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
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
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import com.cryptomorin.xseries.XMaterial;
import com.hongminh54.storage.Database.PlayerData;
import com.hongminh54.storage.GUI.manager.IGUI;
import com.hongminh54.storage.Manager.MineManager;
import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.Chat;
import com.hongminh54.storage.Utils.File;
import com.hongminh54.storage.Utils.GUIText;
import com.hongminh54.storage.Utils.SoundManager;

/**
 * GUI để xem kho khoáng sản của người chơi khác (cả online và offline)
 */
public class ViewPlayerStorageGUI implements IGUI, Listener {
    
    private final Player viewer; // Người xem GUI
    private final String targetName; // Tên người chơi được xem kho
    private Inventory inventory;
    private final List<String> resources = new ArrayList<>();
    private boolean listenerRegistered = false;
    private final boolean isOnline; // Kiểm tra nếu người chơi đang online
    private final PlayerData targetData; // Dữ liệu kho của người chơi mục tiêu
    
    /**
     * Khởi tạo GUI xem kho của người chơi khác
     * 
     * @param viewer Người chơi đang xem kho
     * @param targetName Tên người chơi được xem kho
     */
    public ViewPlayerStorageGUI(Player viewer, String targetName) {
        this.viewer = viewer;
        this.targetName = targetName;
        
        // Kiểm tra xem người chơi mục tiêu có online không
        Player target = Bukkit.getPlayer(targetName);
        this.isOnline = (target != null && target.isOnline());
        
        // Lấy dữ liệu kho từ database nếu người chơi offline, hoặc từ cache nếu online
        if (isOnline) {
            // Người chơi online, lấy dữ liệu từ cache
            this.targetData = null; // Không cần dữ liệu từ database vì sẽ dùng cache
        } else {
            // Người chơi offline, lấy dữ liệu từ database
            this.targetData = Storage.db.getData(targetName);
            
            // Nếu không tìm thấy dữ liệu
            if (this.targetData == null) {
                viewer.sendMessage(Chat.colorizewp("&cKhông tìm thấy dữ liệu kho của người chơi &e" + targetName));
                return;
            }
        }
        
        // Lấy danh sách tài nguyên từ cấu hình
        fetchResources();
        
        // Đăng ký listener
        registerListener();
    }
    
    /**
     * Lấy danh sách tài nguyên từ cấu hình
     */
    private void fetchResources() {
        FileConfiguration config = File.getConfig();
        if (config.contains("items")) {
            resources.addAll(config.getConfigurationSection("items").getKeys(false));
        }
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
     * Hủy đăng ký listener khi đóng GUI
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
        // Nếu không tìm thấy dữ liệu người chơi offline
        if (!isOnline && targetData == null) {
            // Trả về inventory trống
            return Bukkit.createInventory(null, 9, "Không tìm thấy dữ liệu");
        }
        
        // Tạo inventory với kích thước phù hợp
        int rows = Math.min(6, (resources.size() / 9) + 2); // Tối đa 6 hàng, tối thiểu 2 hàng
        inventory = Bukkit.createInventory(null, rows * 9, 
                GUIText.format("&8Kho của: &a" + targetName + (isOnline ? " &2[Online]" : " &c[Offline]")));
        
        // Thêm thông tin người chơi
        addPlayerInfo();
        
        // Thêm các tài nguyên vào GUI
        addResources();
        
        return inventory;
    }
    
    /**
     * Thêm thông tin người chơi vào GUI
     */
    private void addPlayerInfo() {
        // Tạo đầu người chơi để hiển thị
        ItemStack playerHead = XMaterial.PLAYER_HEAD.parseItem();
        if (playerHead != null) {
            SkullMeta meta = (SkullMeta) playerHead.getItemMeta();
            if (meta != null) {
                meta.setOwner(targetName);
                meta.setDisplayName(Chat.colorizewp("&a" + targetName));
                
                List<String> lore = new ArrayList<>();
                lore.add(Chat.colorizewp("&7Trạng thái: " + (isOnline ? "&aOnline" : "&cOffline")));
                
                // Thêm thông tin giới hạn kho
                int maxStorage;
                if (isOnline) {
                    Player target = Bukkit.getPlayer(targetName);
                    maxStorage = (target != null) ? MineManager.getMaxBlock(target) : 0;
                } else {
                    maxStorage = targetData.getMax();
                }
                lore.add(Chat.colorizewp("&7Giới hạn kho: &e" + maxStorage));
                
                meta.setLore(lore);
                playerHead.setItemMeta(meta);
                
                // Đặt vào vị trí giữa hàng đầu tiên
                inventory.setItem(4, playerHead);
            }
        }
        
        // Thêm nút đóng GUI
        ItemStack closeButton = XMaterial.BARRIER.parseItem();
        if (closeButton != null) {
            ItemMeta meta = closeButton.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Chat.colorizewp("&c&lĐóng"));
                closeButton.setItemMeta(meta);
                
                // Đặt vào vị trí góc phải hàng đầu tiên
                inventory.setItem(8, closeButton);
            }
        }
    }
    
    /**
     * Thêm các tài nguyên vào GUI
     */
    private void addResources() {
        int slot = 9; // Bắt đầu từ hàng thứ 2
        
        for (String material : resources) {
            if (slot >= inventory.getSize()) break; // Tránh vượt quá kích thước inventory
            
            // Lấy số lượng tài nguyên
            int amount;
            if (isOnline) {
                Player target = Bukkit.getPlayer(targetName);
                amount = (target != null) ? MineManager.getPlayerBlock(target, material) : 0;
            } else {
                // Phân tích dữ liệu từ chuỗi JSON trong PlayerData
                List<String> dataList = MineManager.convertOnlineData(targetData.getData());
                amount = 0;
                
                for (String data : dataList) {
                    String[] parts = data.split(";");
                    if (parts.length >= 2 && parts[0].equals(material)) {
                        try {
                            amount = Integer.parseInt(parts[1]);
                            break;
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            
            // Tạo item hiển thị
            ItemStack resourceItem;
            try {
                // Thử tìm vật liệu từ tên
                String materialName = material.split(";")[0];
                Material mat = Material.valueOf(materialName);
                resourceItem = new ItemStack(mat);
            } catch (Exception e) {
                // Nếu không tìm thấy, sử dụng vật liệu mặc định
                resourceItem = XMaterial.STONE.parseItem();
            }
            
            if (resourceItem != null) {
                ItemMeta meta = resourceItem.getItemMeta();
                if (meta != null) {
                    // Tên hiển thị lấy từ cấu hình
                    String displayName = File.getConfig().getString("items." + material, material);
                    meta.setDisplayName(Chat.colorizewp("&a" + displayName));
                    
                    List<String> lore = new ArrayList<>();
                    lore.add(Chat.colorizewp("&7Số lượng: &e" + amount));
                    meta.setLore(lore);
                    
                    resourceItem.setItemMeta(meta);
                    inventory.setItem(slot++, resourceItem);
                }
            }
        }
    }
    
    /**
     * Xử lý khi người chơi click vào GUI
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Kiểm tra xem có phải người dùng đang click vào GUI này không
        if (!event.getView().getTopInventory().equals(inventory) || event.getWhoClicked() != viewer) {
            return;
        }
        
        // Luôn hủy tất cả các sự kiện click để ngăn người chơi lấy vật phẩm
        event.setCancelled(true);
        
        // Xử lý khi click vào nút đóng (slot 8)
        if (event.getRawSlot() == 8) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getType() == Material.BARRIER) {
                // Phát âm thanh
                SoundManager.playSound(viewer, Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.0f);
                // Đóng inventory
                viewer.closeInventory();
            }
        }
    }
    
    /**
     * Xử lý khi đóng GUI
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory) && event.getPlayer().equals(viewer)) {
            // Hủy đăng ký listener để tránh rò rỉ bộ nhớ
            Bukkit.getScheduler().runTaskLater(Storage.getStorage(), this::unregisterListener, 1L);
        }
    }
    
    /**
     * Ngăn người chơi kéo vật phẩm trong GUI
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().equals(inventory)) {
            event.setCancelled(true);
        }
    }
} 