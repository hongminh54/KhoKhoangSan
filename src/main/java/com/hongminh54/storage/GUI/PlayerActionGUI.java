package com.hongminh54.storage.GUI;

import java.util.Arrays;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import com.hongminh54.storage.GUI.manager.IGUI;
import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.Chat;
import com.hongminh54.storage.Utils.File;
import com.hongminh54.storage.Utils.GUIText;
import com.hongminh54.storage.Utils.PlayerSearchChatHandler;
import com.hongminh54.storage.Utils.SoundManager;

/**
 * Giao diện thao tác với người chơi được chọn
 */
public class PlayerActionGUI implements IGUI, Listener {
    
    private final Player sender;
    private final Player target;
    private Inventory inventory;
    private boolean listenerRegistered = false;
    
    /**
     * Khởi tạo giao diện thao tác với người chơi
     * @param sender Người gửi
     * @param target Người nhận
     */
    public PlayerActionGUI(Player sender, Player target) {
        this.sender = sender;
        this.target = target;
        
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
        String title = "&8Thao tác với &a" + target.getName();
        inventory = Bukkit.createInventory(sender, 27, GUIText.format(title));
        
        // Thêm viền trang trí
        for (int i = 0; i < 27; i++) {
            if (i < 10 || i > 16 || i % 9 == 0 || i % 9 == 8) {
                ItemStack borderItem;
                try {
                    // Dành cho phiên bản 1.12.2 và cũ hơn
                    borderItem = new ItemStack(Material.valueOf("STAINED_GLASS_PANE"), 1, (short) 7);
                } catch (Exception e) {
                    try {
                        // Dành cho phiên bản 1.13+
                        borderItem = new ItemStack(Material.valueOf("GRAY_STAINED_GLASS_PANE"));
                    } catch (Exception ex) {
                        // Fallback an toàn cho mọi phiên bản
                        borderItem = new ItemStack(Material.STONE_BUTTON);
                    }
                }
                
                ItemMeta meta = borderItem.getItemMeta();
                meta.setDisplayName(Chat.colorize("&r"));
                borderItem.setItemMeta(meta);
                
                inventory.setItem(i, borderItem);
            }
        }
        
        // Thêm nút chuyển một loại tài nguyên
        ItemStack transferSingleButton = new ItemStack(Material.CHEST);
        ItemMeta transferSingleMeta = transferSingleButton.getItemMeta();
        transferSingleMeta.setDisplayName(Chat.colorize("&aChuyển một loại tài nguyên"));
        transferSingleMeta.setLore(Arrays.asList(
            Chat.colorize("&7Chuyển một loại tài nguyên cho"),
            Chat.colorize("&a" + target.getName())
        ));
        transferSingleButton.setItemMeta(transferSingleMeta);
        inventory.setItem(12, transferSingleButton);
        
        // Thêm nút chuyển nhiều loại tài nguyên
        ItemStack transferMultiButton = new ItemStack(Material.ENDER_CHEST);
        ItemMeta transferMultiMeta = transferMultiButton.getItemMeta();
        transferMultiMeta.setDisplayName(Chat.colorize("&aChuyển nhiều loại tài nguyên"));
        transferMultiMeta.setLore(Arrays.asList(
            Chat.colorize("&7Chuyển nhiều loại tài nguyên cho"),
            Chat.colorize("&a" + target.getName())
        ));
        transferMultiButton.setItemMeta(transferMultiMeta);
        inventory.setItem(14, transferMultiButton);
        
        return inventory;
    }
    
    /**
     * Xử lý sự kiện click vào inventory
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        // Kiểm tra nếu không phải GUI này thì không xử lý
        if (e.getView().getTopInventory() != inventory) {
            return;
        }
        
        // Luôn hủy tất cả sự kiện click (kể cả bottom inventory) để ngăn lấy item
        e.setCancelled(true);
        
        // Nếu không phải Player thì không xử lý
        if (!(e.getWhoClicked() instanceof Player)) {
            return; 
        }
        
        Player clicker = (Player) e.getWhoClicked();
        
        // Chỉ xử lý click vào top inventory
        if (e.getClickedInventory() != inventory) {
            return;
        }
        
        // Kiểm tra xem người chơi đích còn online không
        if (!target.isOnline()) {
            clicker.sendMessage(Chat.colorize("&8[&4&l✕&8] &cHiện người chơi &f" + target.getName() + " &ckhông còn online!"));
            SoundManager.playSound(clicker, Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            clicker.closeInventory();
            return;
        }
        
        // Nếu slot không có vật phẩm thì không xử lý
        if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) {
            return;
        }
        
        // Xử lý các nút chức năng
        int slot = e.getRawSlot();
        
        if (slot == 12) {
            // Chuyển một loại tài nguyên
            SoundManager.playSound(clicker, Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            
            // Lưu trữ tham chiếu đến target để sử dụng trong lambda
            final Player finalTarget = target;
            
            // Đóng inventory trước khi mở khung nhập
            clicker.closeInventory();
            
            // Chờ một tick trước khi hiển thị khung nhập để đảm bảo inventory đã đóng hoàn toàn
            Bukkit.getScheduler().runTaskLater(Storage.getStorage(), () -> {
                if (!clicker.isOnline() || !finalTarget.isOnline()) {
                    if (clicker.isOnline()) {
                        clicker.sendMessage(Chat.colorize("&8[&4&l✕&8] &cNgười chơi &f" + finalTarget.getName() + " &ckhông còn trực tuyến!"));
                        SoundManager.playSound(clicker, Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                    }
                    return;
                }
                
                // Người dùng nhập tên tài nguyên
                PlayerSearchChatHandler.startChatInput(
                    clicker, 
                    File.getMessage().getString("user.action.transfer.resource_input", "&8[&b&l❖&8] &bNhập tên tài nguyên để chuyển:"),
                    (material) -> {
                        if (material == null || material.isEmpty()) {
                            clicker.sendMessage(Chat.colorize("&8[&c&l✕&8] &cĐã hủy thao tác chuyển tài nguyên."));
                            return;
                        }
                        
                        // Kiểm tra lại xem người nhận có online không
                        if (!finalTarget.isOnline()) {
                            clicker.sendMessage(Chat.colorize("&8[&4&l✕&8] &cNgười chơi &f" + finalTarget.getName() + " &ckhông còn trực tuyến!"));
                            SoundManager.playSound(clicker, Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                            return;
                        }
                        
                        // Mở giao diện chuyển đơn sau một khoảng thời gian ngắn
                        Bukkit.getScheduler().runTaskLater(Storage.getStorage(), () -> {
                            if (clicker.isOnline() && finalTarget.isOnline()) {
                                clicker.openInventory(new TransferGUI(clicker, finalTarget, material).getInventory());
                            } else if (clicker.isOnline()) {
                                clicker.sendMessage(Chat.colorize("&8[&4&l✕&8] &cNgười chơi &f" + finalTarget.getName() + " &ckhông còn trực tuyến!"));
                                SoundManager.playSound(clicker, Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                            }
                        }, 2L);
                    }
                );
            }, 2L);
            
        } else if (slot == 14) {
            // Chuyển nhiều loại tài nguyên
            SoundManager.playSound(clicker, Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            
            // Lưu trữ tham chiếu đến target để sử dụng trong lambda
            final Player finalTarget = target;
            
            // Đóng inventory trước khi mở GUI mới
            clicker.closeInventory();
            
            // Chờ một tick trước khi mở GUI mới để đảm bảo inventory đã đóng hoàn toàn
            Bukkit.getScheduler().runTaskLater(Storage.getStorage(), () -> {
                // Kiểm tra lại xem người nhận có online không
                if (!clicker.isOnline() || !finalTarget.isOnline()) {
                    if (clicker.isOnline()) {
                        clicker.sendMessage(Chat.colorize("&8[&4&l✕&8] &cNgười chơi &f" + finalTarget.getName() + " &ckhông còn trực tuyến!"));
                        SoundManager.playSound(clicker, Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                    }
                    return;
                }
                
                // Mở giao diện chuyển nhiều loại
                clicker.openInventory(new MultiTransferGUI(clicker, finalTarget).getInventory());
            }, 2L);
        }
    }
    
    /**
     * Xử lý sự kiện đóng inventory
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getInventory().equals(inventory)) {
            // Hủy đăng ký listener khi đóng inventory
            Bukkit.getScheduler().runTaskLater(Storage.getStorage(), this::unregisterListener, 1L);
        }
    }
    
    /**
     * Xử lý sự kiện kéo thả item trong inventory
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getInventory().equals(inventory)) {
            // Hủy tất cả sự kiện kéo thả trong GUI này
            e.setCancelled(true);
        }
    }
} 