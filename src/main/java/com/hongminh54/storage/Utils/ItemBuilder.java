package com.hongminh54.storage.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Lớp tiện ích để xây dựng và tùy chỉnh ItemStack
 */
public class ItemBuilder {
    private final ItemStack itemStack;
    private final ItemMeta meta;
    private final Map<Enchantment, Integer> enchantments = new HashMap<>();
    private final List<ItemFlag> itemFlags = new ArrayList<>();
    
    /**
     * Khởi tạo ItemBuilder với vật liệu chỉ định
     */
    public ItemBuilder(Material material) {
        this(material, 1);
    }
    
    /**
     * Khởi tạo ItemBuilder với vật liệu và số lượng chỉ định
     */
    public ItemBuilder(Material material, int amount) {
        this(material, amount, (byte) 0);
    }
    
    /**
     * Khởi tạo ItemBuilder với vật liệu, số lượng và giá trị data chỉ định
     */
    public ItemBuilder(Material material, int amount, byte data) {
        this.itemStack = new ItemStack(material, amount, data);
        this.meta = itemStack.getItemMeta();
    }
    
    /**
     * Khởi tạo ItemBuilder từ ItemStack
     */
    public ItemBuilder(ItemStack itemStack) {
        this.itemStack = itemStack.clone();
        this.meta = this.itemStack.getItemMeta();
    }
    
    /**
     * Đặt tên hiển thị cho item
     */
    public ItemBuilder setName(String name) {
        if (meta != null) {
            meta.setDisplayName(name);
        }
        return this;
    }
    
    /**
     * Đặt lore cho item
     */
    public ItemBuilder setLore(String... lore) {
        if (meta != null) {
            meta.setLore(Arrays.asList(lore));
        }
        return this;
    }
    
    /**
     * Đặt lore cho item từ danh sách
     */
    public ItemBuilder setLore(List<String> lore) {
        if (meta != null) {
            meta.setLore(lore);
        }
        return this;
    }
    
    /**
     * Thêm dòng lore cho item
     */
    public ItemBuilder addLoreLine(String line) {
        if (meta != null) {
            List<String> lore = meta.getLore();
            if (lore == null) {
                lore = new ArrayList<>();
            }
            lore.add(line);
            meta.setLore(lore);
        }
        return this;
    }
    
    /**
     * Thêm nhiều dòng lore cho item
     */
    public ItemBuilder addLoreLines(String... lines) {
        if (meta != null) {
            List<String> lore = meta.getLore();
            if (lore == null) {
                lore = new ArrayList<>();
            }
            lore.addAll(Arrays.asList(lines));
            meta.setLore(lore);
        }
        return this;
    }
    
    /**
     * Thêm enchant cho item
     */
    public ItemBuilder addEnchant(Enchantment enchantment, int level) {
        enchantments.put(enchantment, level);
        return this;
    }
    
    /**
     * Thêm flag cho item
     */
    public ItemBuilder addItemFlag(ItemFlag flag) {
        itemFlags.add(flag);
        return this;
    }
    
    /**
     * Thêm nhiều flag cho item
     */
    public ItemBuilder addItemFlags(ItemFlag... flags) {
        itemFlags.addAll(Arrays.asList(flags));
        return this;
    }
    
    /**
     * Đặt item không thể bị phá hủy
     */
    public ItemBuilder setUnbreakable(boolean unbreakable) {
        if (meta != null) {
            meta.spigot().setUnbreakable(unbreakable);
        }
        return this;
    }
    
    /**
     * Thiết lập số lượng item
     */
    public ItemBuilder setAmount(int amount) {
        itemStack.setAmount(amount);
        return this;
    }
    
    /**
     * Tạo hiệu ứng phát sáng cho item
     */
    public ItemBuilder setGlow(boolean glow) {
        if (glow) {
            addEnchant(Enchantment.DURABILITY, 1);
            addItemFlag(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }
    
    /**
     * Xây dựng và trả về ItemStack đã được tùy chỉnh
     */
    public ItemStack build() {
        if (meta != null) {
            // Áp dụng enchantments
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                meta.addEnchant(entry.getKey(), entry.getValue(), true);
            }
            
            // Áp dụng itemFlags
            for (ItemFlag flag : itemFlags) {
                meta.addItemFlags(flag);
            }
            
            // Áp dụng meta vào itemStack
            itemStack.setItemMeta(meta);
        }
        
        return itemStack;
    }
} 