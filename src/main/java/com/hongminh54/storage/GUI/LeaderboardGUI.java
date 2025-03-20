package com.hongminh54.storage.GUI;

import java.util.ArrayList;
import java.util.List;
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
import org.bukkit.inventory.meta.SkullMeta;
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

public class LeaderboardGUI implements IGUI, Listener {
    private final Player p;
    private Inventory inventory;
    private final FileConfiguration fileConfig;
    private String currentType;
    private boolean listenerRegistered = false;

    public LeaderboardGUI(Player p) {
        this(p, LeaderboardManager.TYPE_MINED);
    }
    
    public LeaderboardGUI(Player p, String type) {
        this.p = p;
        this.fileConfig = File.getGUIConfig("leaderboard");
        this.currentType = type;
        createInventory();
        
        // Đăng ký Listener khi tạo GUI
        registerListener();
    }

    private void createInventory() {
        String title = GUIText.format(Objects.requireNonNull(fileConfig.getString("title"))
                .replace("#type#", LeaderboardManager.getTypeDisplayName(currentType)));
        int size = fileConfig.getInt("size") * 9;
        inventory = GUI.createInventory(p, size, title);
        fillItems();
    }

    private void fillItems() {
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
                fillRankItems(item, LeaderboardManager.getLeaderboard(currentType, 100));
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
                            fillItems();
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
        boolean useSkull = section.getBoolean("use_player_head", false);
        Material material;
        if (useSkull) {
            material = XMaterial.PLAYER_HEAD.parseMaterial();
        } else {
            material = XMaterial.matchXMaterial(section.getString("material")).get().parseMaterial();
        }
        
        int startSlot = section.getInt("start_slot");
        boolean enchanted = section.getBoolean("enchanted", false);
        
        String nameFormat = section.getString("name_format", "&f#rank#. &e#player#");
        List<String> loreFormat = section.getStringList("lore_format");
        
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
        for (int i = 0; i < leaderboard.size() && i < 10; i++) {
            LeaderboardEntry entry = leaderboard.get(i);
            int rank = i + 1;
            int slot = startSlot + i;
            
            // Tạo item hiển thị
            ItemStack itemStack = new ItemStack(material);
            ItemMeta meta = itemStack.getItemMeta();
            
            if (meta != null) {
                // Nếu sử dụng đầu người chơi
                if (useSkull && meta instanceof SkullMeta) {
                    SkullMeta skullMeta = (SkullMeta) meta;
                    Player targetPlayer = Bukkit.getPlayer(entry.getPlayerName());
                    if (targetPlayer != null) {
                        skullMeta.setOwningPlayer(targetPlayer);
                    } else {
                        try {
                            skullMeta.setOwner(entry.getPlayerName());
                        } catch (Exception e) {
                            Storage.getStorage().getLogger().warning("Không thể đặt chủ sở hữu đầu: " + e.getMessage());
                        }
                    }
                }
                
                // Cài đặt tên hiển thị
                String displayName = GUIText.format(nameFormat
                        .replace("#rank#", String.valueOf(rank))
                        .replace("#player#", entry.getDisplayName()));
                meta.setDisplayName(displayName);
                
                // Cài đặt lore
                List<String> lore = new ArrayList<>();
                for (String line : loreFormat) {
                    String formattedLine = line
                            .replace("#value#", String.valueOf(entry.getValue()))
                            .replace("#type#", LeaderboardManager.getTypeDisplayName(currentType));
                    lore.add(GUIText.format(formattedLine));
                }
                meta.setLore(lore);
                
                // Thêm các flags
                for (ItemFlag flag : flags) {
                    meta.addItemFlags(flag);
                }
                
                itemStack.setItemMeta(meta);
            }
            
            // Tạo InteractiveItem
            InteractiveItem interactiveItem = new InteractiveItem(itemStack, slot, enchanted, new ArrayList<>(), "rank_" + rank);
            if (enchanted) {
                interactiveItem.enchant();
            }
            
            inventory.setItem(slot, interactiveItem.getItem());
            GUI.getInteractiveItems().put(slot, interactiveItem);
        }
        
        // Nếu không có người chơi trong bảng xếp hạng, hiển thị thông báo
        if (leaderboard.isEmpty()) {
            ItemStack emptyItem = new ItemStack(Material.PAPER);
            ItemMeta meta = emptyItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Chat.colorizewp("&cKhông có dữ liệu"));
                List<String> lore = new ArrayList<>();
                lore.add(Chat.colorizewp("&7Chưa có người chơi nào trong bảng xếp hạng này"));
                meta.setLore(lore);
                emptyItem.setItemMeta(meta);
            }
            
            inventory.setItem(startSlot, emptyItem);
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