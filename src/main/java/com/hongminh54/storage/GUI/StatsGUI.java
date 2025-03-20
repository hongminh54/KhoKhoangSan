package com.hongminh54.storage.GUI;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
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
import com.hongminh54.storage.Utils.File;
import com.hongminh54.storage.Utils.GUIText;
import com.hongminh54.storage.Utils.Number;
import com.hongminh54.storage.Utils.StatsManager;

public class StatsGUI implements IGUI {
    private final Player p;
    private Inventory inventory;
    private final FileConfiguration fileConfig;

    public StatsGUI(Player p) {
        this.p = p;
        this.fileConfig = File.getGUIConfig("stats");
        createInventory();
    }

    private void createInventory() {
        String title = GUIText.format(Objects.requireNonNull(fileConfig.getString("title")));
        int size = fileConfig.getInt("size") * 9;
        inventory = GUI.createInventory(p, size, title);
        fillItems();
    }

    private void fillItems() {
        ConfigurationSection items = fileConfig.getConfigurationSection("items");
        if (items == null) return;

        for (String key : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(key);
            if (item == null) continue;

            Material material = XMaterial.matchXMaterial(item.getString("material")).get().parseMaterial();
            int amount = item.getInt("amount", 1);
            int slot = item.getInt("slot");
            short data = (short) item.getInt("data", 0);
            
            // Sử dụng GUIText để định dạng tên và mô tả
            String name = GUIText.format(item.getString("name", ""));
            List<String> lore = new ArrayList<>();

            for (String loreLine : item.getStringList("lore")) {
                // Thay thế placeholder với giá trị thống kê đã được định dạng
                loreLine = loreLine.replace("#total_mined#", 
                        Number.formatCompact(StatsManager.getTotalMined(p)))
                        .replace("#total_deposited#", 
                        Number.formatCompact(StatsManager.getTotalDeposited(p)))
                        .replace("#total_withdrawn#", 
                        Number.formatCompact(StatsManager.getTotalWithdrawn(p)))
                        .replace("#total_sold#", 
                        Number.formatCompact(StatsManager.getTotalSold(p)));
                
                lore.add(GUIText.format(loreLine));
            }

            boolean enchanted = item.getBoolean("enchanted", false);
            List<String> actions = item.getStringList("action");

            ItemStack itemStack = new ItemStack(material, amount, data);
            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(name);
                meta.setLore(lore);
                
                // Xử lý flags nếu có
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

            InteractiveItem interactiveItem = new InteractiveItem(itemStack, slot, enchanted, actions, key);
            if (enchanted) {
                interactiveItem.enchant();
            }
            
            // Xử lý các hành động khi click vào item
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
                    }
                }
            });

            inventory.setItem(slot, interactiveItem.getItem());
            GUI.getInteractiveItems().put(slot, interactiveItem);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
} 