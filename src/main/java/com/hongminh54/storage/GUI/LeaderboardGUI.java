package com.hongminh54.storage.GUI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import com.cryptomorin.xseries.XMaterial;
import com.hongminh54.storage.GUI.manager.IGUI;
import com.hongminh54.storage.GUI.manager.InteractiveItem;
import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.Chat;
import com.hongminh54.storage.Utils.File;
import com.hongminh54.storage.Utils.GUIText;
import com.hongminh54.storage.Utils.LeaderboardManager;
import com.hongminh54.storage.Utils.LeaderboardManager.LeaderboardEntry;
import com.hongminh54.storage.Utils.Number;

public class LeaderboardGUI implements IGUI, Listener {
    private final Player p;
    private Inventory inventory;
    private final FileConfiguration fileConfig;
    private String currentType;
    private boolean listenerRegistered = false;

    private static final Map<String, Long> lastRefreshTimes = new HashMap<>();
    private static final long REFRESH_COOLDOWN = 2000; // 2 giây giữa các lần làm mới
    private static final int LEADERBOARD_DISPLAY_LIMIT = 20; // Giới hạn số người hiển thị

    public LeaderboardGUI(Player p) {
        this(p, LeaderboardManager.TYPE_MINED);
    }
    
    public LeaderboardGUI(Player p, String type) {
        this.p = p;
        this.fileConfig = File.getGUIConfig("leaderboard");
        this.currentType = type;
        
        // Tạo GUI với tối ưu hiệu suất
        createInventory();
        
        // Đăng ký Listener khi tạo GUI
        registerListener();
    }

    private void createInventory() {
        String title = GUIText.format(Objects.requireNonNull(fileConfig.getString("title"))
                .replace("#type#", LeaderboardManager.getTypeDisplayName(currentType)));
        int size = fileConfig.getInt("size") * 9;
        
        // Tạo inventory mới
        inventory = GUI.createInventory(p, size, title);
        
        // Lấy thời gian làm mới cuối cùng
        String playerKey = p.getName() + "_" + currentType;
        Long lastRefresh = lastRefreshTimes.get(playerKey);
        long currentTime = System.currentTimeMillis();
        
        // Nếu mới vừa làm mới gần đây, không cần truy vấn lại database
        if (lastRefresh != null && (currentTime - lastRefresh) < REFRESH_COOLDOWN) {
            // Chỉ hiển thị lại các item mà không truy vấn database
            fillItems(false);
        } else {
            // Cập nhật thời gian làm mới
            lastRefreshTimes.put(playerKey, currentTime);
            // Hiển thị với dữ liệu mới từ database
            fillItems(true);
        }
    }

    private void fillItems(boolean forceRefresh) {
        // Xóa các item cũ trong inventory
        inventory.clear();
        GUI.getInteractiveItems().clear();
        
        ConfigurationSection items = fileConfig.getConfigurationSection("items");
        if (items == null) return;

        // Lấy xếp hạng của người chơi hiện tại
        int playerRank = LeaderboardManager.getPlayerRank(p, currentType);

        for (String key : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(key);
            if (item == null) continue;

            // Xử lý trường hợp đặc biệt cho các mục xếp hạng
            if (key.equals("rank_item")) {
                // Hiển thị danh sách người chơi xếp hạng cao nhất
                // Giới hạn số lượng kết quả để tránh quá tải
                List<LeaderboardEntry> leaderboard;
                if (forceRefresh) {
                    // Chỉ lấy thông tin từ database khi cần làm mới
                    leaderboard = LeaderboardManager.getLeaderboard(currentType, LEADERBOARD_DISPLAY_LIMIT);
                } else {
                    // Sử dụng cache nếu không cần làm mới
                    leaderboard = LeaderboardManager.getCachedLeaderboard(currentType, LEADERBOARD_DISPLAY_LIMIT);
                }
                fillRankItems(item, leaderboard);
                continue;
            }
            
            // Xử lý các item thông thường
            Material material = XMaterial.matchXMaterial(item.getString("material")).get().parseMaterial();
            int amount = item.getInt("amount", 1);
            String slotStr = item.getString("slot");
            short data = (short) item.getInt("data", 0);
            
            // Xử lý nhiều slot
            List<Integer> slots = new ArrayList<>();
            if (slotStr != null && slotStr.contains(",")) {
                String[] slotArray = slotStr.split(",");
                for (String slot : slotArray) {
                    try {
                        slots.add(Integer.parseInt(slot.trim()));
                    } catch (NumberFormatException ignored) {
                        // Bỏ qua các giá trị không hợp lệ
                    }
                }
            } else {
                try {
                    slots.add(Integer.parseInt(slotStr));
                } catch (NumberFormatException ignored) {
                    // Bỏ qua nếu không phải số
                }
            }
            
            // Sử dụng GUIText để định dạng tên và mô tả
            String name = GUIText.format(item.getString("name", ""));
            List<String> lore = new ArrayList<>();

            for (String loreLine : item.getStringList("lore")) {
                // Thay thế placeholder
                loreLine = loreLine.replace("#player_rank#", playerRank > 0 ? String.valueOf(playerRank) : "Không xếp hạng")
                        .replace("#type#", LeaderboardManager.getTypeDisplayName(currentType));
                
                lore.add(GUIText.format(loreLine));
            }

            boolean enchanted = item.getBoolean("enchanted", false);
            List<String> actions = item.getStringList("action");

            ItemStack itemStack = new ItemStack(material, amount, data);
            ItemMeta meta = itemStack.getItemMeta();
            
            if (meta != null) {
                meta.setDisplayName(name);
                meta.setLore(lore);
                
                // Thêm các flags
                if (item.contains("flags")) {
                    ConfigurationSection flagsSection = item.getConfigurationSection("flags");
                    if (flagsSection != null) {
                        for (String flag_name : flagsSection.getKeys(false)) {
                            boolean apply = flagsSection.getBoolean(flag_name);
                            if (flag_name.equalsIgnoreCase("ALL") && apply) {
                                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES,
                                        ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_ENCHANTS,
                                        ItemFlag.HIDE_PLACED_ON, ItemFlag.HIDE_POTION_EFFECTS);
                                break;
                            } else if (apply) {
                                try {
                                    meta.addItemFlags(ItemFlag.valueOf(flag_name));
                                } catch (IllegalArgumentException ignored) {
                                    // Bỏ qua flag không hợp lệ
                                }
                            }
                        }
                    }
                }
                
                itemStack.setItemMeta(meta);
            }
            
            // Tạo InteractiveItem và đặt vào tất cả các slot
            for (int slot : slots) {
                InteractiveItem interactiveItem = new InteractiveItem(itemStack, slot, enchanted, actions, key);
                if (enchanted) {
                    interactiveItem.enchant();
                }

                interactiveItem.onClick((player, clickType) -> {
                    for (String action : actions) {
                        if (action.startsWith("[PLAYER_COMMAND]")) {
                            String command = action.substring("[PLAYER_COMMAND]".length()).trim();
                            player.closeInventory();
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    player.performCommand(command);
                                }
                            }.runTask(Storage.getStorage());
                        } else if (action.startsWith("[SWITCH_TYPE]")) {
                            String newType = action.substring("[SWITCH_TYPE]".length()).trim();
                            
                            // Chuyển đổi loại bảng xếp hạng
                            switch (newType) {
                                case "mined":
                                    currentType = LeaderboardManager.TYPE_MINED;
                                    break;
                                case "deposited":
                                    currentType = LeaderboardManager.TYPE_DEPOSITED;
                                    break;
                                case "withdrawn":
                                    currentType = LeaderboardManager.TYPE_WITHDRAWN;
                                    break;
                                case "sold":
                                    currentType = LeaderboardManager.TYPE_SOLD;
                                    break;
                            }
                            
                            // Tạo lại inventory với loại mới
                            String newTitle = GUIText.format(Objects.requireNonNull(fileConfig.getString("title"))
                                    .replace("#type#", LeaderboardManager.getTypeDisplayName(currentType)));
                            int newSize = fileConfig.getInt("size") * 9;
                            inventory = GUI.createInventory(p, newSize, newTitle);
                            fillItems(true);
                            player.openInventory(inventory);
                        }
                    }
                });

                inventory.setItem(slot, interactiveItem.getItem());
                GUI.getInteractiveItems().put(slot, interactiveItem);
            }
        }
    }
    
    /**
     * Hiển thị các mục xếp hạng
     */
    private void fillRankItems(ConfigurationSection section, List<LeaderboardEntry> leaderboard) {
        // Không sử dụng đầu người chơi mà luôn dùng PAPER để tối ưu hiệu suất
        boolean useSkull = false; // Luôn đặt thành false để không dùng đầu người chơi
        Material material = Material.PAPER; // Luôn sử dụng PAPER thay vì đầu người chơi
        
        int startSlot = section.getInt("start_slot");
        boolean enchanted = section.getBoolean("enchanted", false);
        
        String nameFormat = section.getString("name_format", "&f#rank#. &e#player#");
        List<String> loreFormat = section.getStringList("lore_format");
        
        // Giới hạn số lượng người chơi hiển thị để tránh quá tải
        int displayLimit = Math.min(LEADERBOARD_DISPLAY_LIMIT, section.getInt("max_display", LEADERBOARD_DISPLAY_LIMIT));
        
        // Hiển thị thông báo "Đang tải..." nếu không có dữ liệu
        if (leaderboard.isEmpty()) {
            ItemStack loadingItem = new ItemStack(material);
            ItemMeta meta = loadingItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(GUIText.format("&eĐang tải dữ liệu..."));
                meta.setLore(Collections.singletonList(GUIText.format("&7Vui lòng đợi trong giây lát")));
                loadingItem.setItemMeta(meta);
            }
            
            // Chỉ hiển thị một vài item "Đang tải..." để không tạo quá nhiều item
            for (int i = 0; i < Math.min(5, displayLimit); i++) {
                inventory.setItem(startSlot + i, loadingItem);
            }
            return;
        }
        
        // Xử lý flags
        List<ItemFlag> flags = new ArrayList<>();
        if (section.contains("flags")) {
            ConfigurationSection flagsSection = section.getConfigurationSection("flags");
            if (flagsSection != null) {
                for (String flag_name : flagsSection.getKeys(false)) {
                    boolean apply = flagsSection.getBoolean(flag_name);
                    if (flag_name.equalsIgnoreCase("ALL") && apply) {
                        flags.add(ItemFlag.HIDE_UNBREAKABLE);
                        flags.add(ItemFlag.HIDE_ATTRIBUTES);
                        flags.add(ItemFlag.HIDE_DESTROYS);
                        flags.add(ItemFlag.HIDE_ENCHANTS);
                        flags.add(ItemFlag.HIDE_PLACED_ON);
                        flags.add(ItemFlag.HIDE_POTION_EFFECTS);
                        break;
                    } else if (apply) {
                        try {
                            flags.add(ItemFlag.valueOf(flag_name));
                        } catch (IllegalArgumentException ignored) {
                            // Bỏ qua flag không hợp lệ
                        }
                    }
                }
            }
        }
        
        // Hiển thị danh sách xếp hạng
        for (int i = 0; i < leaderboard.size() && i < displayLimit; i++) {
            LeaderboardEntry entry = leaderboard.get(i);
            int rank = i + 1;
            
            // Tính slot một cách thông minh để tránh đè lên viền
            int slot;
            
            // 3 slot đầu tiên trong hàng đầu: 10, 11, 12, 13, 14, 15, 16
            // 3 slot đầu tiên trong hàng thứ hai: 19, 20, 21, 22, 23, 24, 25
            // 3 slot đầu tiên trong hàng thứ ba: 28, 29, 30, 31, 32, 33, 34
            if (i < 7) {
                // Đặt vào hàng đầu tiên (slot 10-16)
                slot = startSlot + i;
            } else if (i < 14) {
                // Đặt vào hàng thứ hai (slot 19-25)
                slot = startSlot + 9 + (i - 7);
            } else {
                // Đặt vào hàng thứ ba (slot 28-34)
                slot = startSlot + 18 + (i - 14);
            }
            
            // Luôn tạo item từ PAPER
            ItemStack itemStack = new ItemStack(material);
            ItemMeta meta = itemStack.getItemMeta();
            
            if (meta != null) {
                // Tô màu cho item theo hạng
                String rankColor = "&f";
                if (rank == 1) rankColor = "&e"; // Hạng 1: Vàng
                else if (rank == 2) rankColor = "&7"; // Hạng 2: Bạc
                else if (rank == 3) rankColor = "&6"; // Hạng 3: Đồng
                
                // Đặt tên và mô tả cho item
                String name = nameFormat.replace("#rank#", rankColor + rank)
                        .replace("#player#", entry.getDisplayName());
                meta.setDisplayName(GUIText.format(name));
                
                List<String> lore = new ArrayList<>();
                for (String loreLine : loreFormat) {
                    // Sử dụng định dạng số mới
                    loreLine = loreLine.replace("#value#", Number.formatCompact(entry.getValue()))
                            .replace("#type#", LeaderboardManager.getTypeDisplayName(currentType));
                    lore.add(GUIText.format(loreLine));
                }
                meta.setLore(lore);
                
                // Thêm flags
                for (ItemFlag flag : flags) {
                    meta.addItemFlags(flag);
                }
                
                itemStack.setItemMeta(meta);
            }
            
            // Thêm vào GUI
            inventory.setItem(slot, itemStack);
            
            // Tạo InteractiveItem nếu cần enchanted effect hoặc action nào đó
            if (enchanted) {
                List<String> actions = new ArrayList<>();
                InteractiveItem interactiveItem = new InteractiveItem(itemStack, slot, enchanted, actions, "rank_" + rank);
                GUI.getInteractiveItems().put(slot, interactiveItem);
            }
        }
        
        // Nếu không có người chơi trong bảng xếp hạng, hiển thị thông báo
        if (leaderboard.isEmpty()) {
            ItemStack emptyItem = new ItemStack(Material.PAPER);
            ItemMeta meta = emptyItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Chat.colorizewp("&cKhông có dữ liệu"));
                List<String> lore = new ArrayList<>();
                lore.add(Chat.colorizewp("&7Chưa có người chơi nào trong bảng xếp hạng này"));
                lore.add(Chat.colorizewp("&7Hãy là người đầu tiên xuất hiện ở đây!"));
                meta.setLore(lore);
                emptyItem.setItemMeta(meta);
            }
            
            // Hiển thị thông báo ở các slot đầu tiên của mỗi hàng để dễ nhìn
            int[] emptySlots = {startSlot, startSlot + 9, startSlot + 18};
            for (int emptySlot : emptySlots) {
                inventory.setItem(emptySlot, emptyItem);
            }
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
     * Hủy đăng ký listener
     */
    private void unregisterListener() {
        if (listenerRegistered) {
            HandlerList.unregisterAll(this);
            listenerRegistered = false;
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
    
    /**
     * Xử lý sự kiện click vào inventory để ngăn lấy vật phẩm
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().equals(inventory)) {
            event.setCancelled(true);
            
            // Xử lý hành động click thông qua InteractiveItem
            int slot = event.getRawSlot();
            if (GUI.getInteractiveItems().containsKey(slot)) {
                InteractiveItem item = GUI.getInteractiveItems().get(slot);
                item.handleClick((Player) event.getWhoClicked(), event.getClick());
            }
        }
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
} 