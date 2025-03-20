package com.hongminh54.storage.Manager;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.XMaterial;
import com.hongminh54.storage.NMS.NMSAssistant;
import com.hongminh54.storage.Utils.Chat;
import com.hongminh54.storage.Utils.File;

public class ItemManager {

    /**
     * Tạo ItemStack từ Material, tên và danh sách lore
     * @param material Loại vật liệu
     * @param name Tên hiển thị
     * @param lore Danh sách mô tả
     * @return ItemStack đã tạo
     */
    public static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Chat.colorize(name));
            meta.setLore(Chat.colorizewp(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    public static ItemStack getItemConfig(ConfigurationSection section) {
        ItemStack itemStack;
        String m_s = section.getString("material");
        Optional<XMaterial> xMaterialOptional = XMaterial.matchXMaterial(m_s != null ? m_s : "BLACK_STAINED_GLASS_PANE");
        if (xMaterialOptional.isPresent() && xMaterialOptional.get().parseItem() != null) {
            itemStack = xMaterialOptional.get().parseItem();
        } else {
            // Fallback vật liệu cho phiên bản cũ
            try {
                itemStack = new ItemStack(Material.valueOf("STAINED_GLASS_PANE"), 1, (short) 15);
            } catch (IllegalArgumentException e) {
                // Nếu không tìm thấy STAINED_GLASS_PANE, sử dụng STONE làm fallback
                itemStack = new ItemStack(Material.STONE);
            }
        }
        ItemMeta meta = itemStack.getItemMeta();
        NMSAssistant nms = new NMSAssistant();
        if (nms.isVersionLessThanOrEqualTo(13)) {
            itemStack.setDurability((short) section.getInt("damage"));
        }
        if (nms.isVersionGreaterThanOrEqualTo(14)) {
            meta.setCustomModelData(section.getInt("custom-model-data"));
        }
        itemStack.setAmount(section.getInt("amount"));
        meta.setUnbreakable(section.getBoolean("unbreakable"));
        meta.setLore(Chat.colorizewp(section.getStringList("lore")));
        meta.setDisplayName(Chat.colorizewp(section.getString("name")));
        if (section.contains("enchants")) {
            for (String enchant_name : Objects.requireNonNull(section.getConfigurationSection("enchants")).getKeys(false)) {
                int level = section.getInt("enchants." + enchant_name);
                Optional<XEnchantment> enchantment = XEnchantment.matchXEnchantment(enchant_name);
                if (enchantment.isPresent() && enchantment.get().getEnchant() != null) {
                    meta.addEnchant(enchantment.get().getEnchant(), level, false);
                }
            }
        }
        if (section.contains("flags")) {
            for (String flag_name : Objects.requireNonNull(section.getConfigurationSection("flags")).getKeys(false)) {
                boolean apply = section.getBoolean("flags." + flag_name);
                if (flag_name.equalsIgnoreCase("ALL")) {
                    if (apply) {
                        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_PLACED_ON, ItemFlag.HIDE_POTION_EFFECTS);
                        break;
                    }
                } else {
                    meta.addItemFlags(ItemFlag.valueOf(flag_name));
                }
            }
        }
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public static ItemStack getItemConfig(Player p, String material, ConfigurationSection section) {
        String m_s = section.getString("material");
        Optional<XMaterial> xMaterialOptional = XMaterial.matchXMaterial(m_s != null ? m_s : "BLACK_STAINED_GLASS_PANE");
        ItemStack itemStack;
        if (xMaterialOptional.isPresent() && xMaterialOptional.get().parseItem() != null) {
            itemStack = xMaterialOptional.get().parseItem();
        } else {
            // Fallback vật liệu cho phiên bản cũ
            try {
                itemStack = new ItemStack(Material.valueOf("STAINED_GLASS_PANE"), 1, (short) 15);
            } catch (IllegalArgumentException e) {
                // Nếu không tìm thấy STAINED_GLASS_PANE, sử dụng STONE làm fallback
                itemStack = new ItemStack(Material.STONE);
            }
        }
        ItemMeta meta = itemStack.getItemMeta();
        NMSAssistant nms = new NMSAssistant();
        if (nms.isVersionLessThanOrEqualTo(13)) {
            itemStack.setDurability((short) section.getInt("damage"));
        }
        if (nms.isVersionGreaterThanOrEqualTo(14)) {
            meta.setCustomModelData(section.getInt("custom-model-data"));
        }
        itemStack.setAmount(section.getInt("amount"));
        meta.setUnbreakable(section.getBoolean("unbreakable"));
        meta.setLore(Chat.colorizewp(section.getStringList("lore")
                .stream().map(s -> s.replace("#item_amount#", String.valueOf(MineManager.getPlayerBlock(p, material)))
                        .replace("#max_storage#", String.valueOf(MineManager.getMaxBlock(p)))
                        .replace("#material#", Objects.requireNonNull(File.getConfig().getString("items." + material)))).collect(Collectors.toList())));
        meta.setDisplayName(Chat.colorizewp(Objects.requireNonNull(section.getString("name"))
                .replace("#item_amount#", String.valueOf(MineManager.getPlayerBlock(p, material)))
                .replace("#max_storage#", String.valueOf(MineManager.getMaxBlock(p)))
                .replace("#material#", Objects.requireNonNull(File.getConfig().getString("items." + material)))));
        if (section.contains("enchants")) {
            for (String enchant_name : Objects.requireNonNull(section.getConfigurationSection("enchants")).getKeys(false)) {
                int level = section.getInt("enchants." + enchant_name);
                Optional<XEnchantment> enchantment = XEnchantment.matchXEnchantment(enchant_name);
                if (enchantment.isPresent() && enchantment.get().getEnchant() != null) {
                    meta.addEnchant(enchantment.get().getEnchant(), level, false);
                }
            }
        }
        if (section.contains("flags")) {
            for (String flag_name : Objects.requireNonNull(section.getConfigurationSection("flags")).getKeys(false)) {
                boolean apply = section.getBoolean("flags." + flag_name);
                if (flag_name.equalsIgnoreCase("ALL")) {
                    if (apply) {
                        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_PLACED_ON, ItemFlag.HIDE_POTION_EFFECTS);
                        break;
                    }
                } else {
                    meta.addItemFlags(ItemFlag.valueOf(flag_name));
                }
            }
        }
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public static String getStatus(Player p) {
        if (MineManager.toggle.get(p)) {
            return Chat.colorizewp(File.getMessage().getString("user.status.status_on"));
        } else return Chat.colorizewp(File.getMessage().getString("user.status.status_off"));
    }

    public static ItemStack getItemConfig(Player p, ConfigurationSection section) {
        String m_s = section.getString("material");
        Optional<XMaterial> xMaterialOptional = XMaterial.matchXMaterial(m_s != null ? m_s : "BLACK_STAINED_GLASS_PANE");
        ItemStack itemStack;
        if (xMaterialOptional.isPresent() && xMaterialOptional.get().parseItem() != null) {
            itemStack = xMaterialOptional.get().parseItem();
        } else {
            // Fallback vật liệu cho phiên bản cũ
            try {
                itemStack = new ItemStack(Material.valueOf("STAINED_GLASS_PANE"), 1, (short) 15);
            } catch (IllegalArgumentException e) {
                // Nếu không tìm thấy STAINED_GLASS_PANE, sử dụng STONE làm fallback
                itemStack = new ItemStack(Material.STONE);
            }
        }
        ItemMeta meta = itemStack.getItemMeta();
        NMSAssistant nms = new NMSAssistant();
        if (nms.isVersionLessThanOrEqualTo(13)) {
            itemStack.setDurability((short) section.getInt("damage"));
        }
        if (nms.isVersionGreaterThanOrEqualTo(14)) {
            meta.setCustomModelData(section.getInt("custom-model-data"));
        }
        itemStack.setAmount(section.getInt("amount"));
        meta.setUnbreakable(section.getBoolean("unbreakable"));
        meta.setLore(Chat.colorizewp(section.getStringList("lore")
                .stream().map(s -> s.replace("#status#", getStatus(p))).collect(Collectors.toList())));
        meta.setDisplayName(Chat.colorizewp(section.getString("name")));
        if (section.contains("enchants")) {
            for (String enchant_name : Objects.requireNonNull(section.getConfigurationSection("enchants")).getKeys(false)) {
                int level = section.getInt("enchants." + enchant_name);
                Optional<XEnchantment> enchantment = XEnchantment.matchXEnchantment(enchant_name);
                if (enchantment.isPresent() && enchantment.get().getEnchant() != null) {
                    meta.addEnchant(enchantment.get().getEnchant(), level, false);
                }
            }
        }
        if (section.contains("flags")) {
            for (String flag_name : Objects.requireNonNull(section.getConfigurationSection("flags")).getKeys(false)) {
                boolean apply = section.getBoolean("flags." + flag_name);
                if (flag_name.equalsIgnoreCase("ALL")) {
                    if (apply) {
                        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_PLACED_ON, ItemFlag.HIDE_POTION_EFFECTS);
                        break;
                    }
                } else {
                    meta.addItemFlags(ItemFlag.valueOf(flag_name));
                }
            }
        }
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public static ItemStack getItemConfig(Player p, String material, String name, ConfigurationSection section) {
        String materialName = material.split(";")[0];
        Optional<XMaterial> xMaterialOptional = XMaterial.matchXMaterial(materialName != null ? materialName : "BLACK_STAINED_GLASS_PANE");
        ItemStack itemStack;
        if (xMaterialOptional.isPresent() && xMaterialOptional.get().parseItem() != null) {
            itemStack = xMaterialOptional.get().parseItem();
        } else {
            // Fallback vật liệu cho phiên bản cũ
            try {
                itemStack = new ItemStack(Material.valueOf("STAINED_GLASS_PANE"), 1, (short) 15);
            } catch (IllegalArgumentException e) {
                // Nếu không tìm thấy STAINED_GLASS_PANE, sử dụng STONE làm fallback
                itemStack = new ItemStack(Material.STONE);
            }
        }
        ItemMeta meta = itemStack.getItemMeta();
        NMSAssistant nms = new NMSAssistant();
        if (nms.isVersionLessThanOrEqualTo(13)) {
            itemStack.setDurability((short) section.getInt("damage"));
        }
        if (nms.isVersionGreaterThanOrEqualTo(14)) {
            meta.setCustomModelData(section.getInt("custom-model-data"));
        }
        itemStack.setAmount(section.getInt("amount"));
        meta.setUnbreakable(section.getBoolean("unbreakable"));
        meta.setLore(Chat.colorizewp(section.getStringList("lore")
                .stream().map(s -> s.replace("#item_amount#", String.valueOf(MineManager.getPlayerBlock(p, material)))
                        .replace("#max_storage#", String.valueOf(MineManager.getMaxBlock(p)))).collect(Collectors.toList())));
        meta.setDisplayName(Chat.colorizewp(name));
        if (section.contains("enchants")) {
            for (String enchant_name : Objects.requireNonNull(section.getConfigurationSection("enchants")).getKeys(false)) {
                int level = section.getInt("enchants." + enchant_name);
                Optional<XEnchantment> enchantment = XEnchantment.matchXEnchantment(enchant_name);
                if (enchantment.isPresent() && enchantment.get().getEnchant() != null) {
                    meta.addEnchant(enchantment.get().getEnchant(), level, false);
                }
            }
        }
        if (section.contains("flags")) {
            for (String flag_name : Objects.requireNonNull(section.getConfigurationSection("flags")).getKeys(false)) {
                boolean apply = section.getBoolean("flags." + flag_name);
                if (flag_name.equalsIgnoreCase("ALL")) {
                    if (apply) {
                        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_PLACED_ON, ItemFlag.HIDE_POTION_EFFECTS);
                        break;
                    }
                } else {
                    meta.addItemFlags(ItemFlag.valueOf(flag_name));
                }
            }
        }
        itemStack.setItemMeta(meta);
        return itemStack;
    }
}
