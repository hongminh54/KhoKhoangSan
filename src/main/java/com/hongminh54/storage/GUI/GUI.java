package com.hongminh54.storage.GUI;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import com.hongminh54.storage.GUI.listeners.GUIClickListener;
import com.hongminh54.storage.GUI.manager.InteractiveItem;

public class GUI {
    /**
     * The item mapper, which is used to recognize which item was clicked based on NBT tag.
     */
    private static final HashMap<UUID, InteractiveItem> itemMapper = new HashMap<>();
    
    /**
     * The interactive items map for slots in GUI
     */
    private static final Map<Integer, InteractiveItem> interactiveItems = new HashMap<>();

    public static HashMap<UUID, InteractiveItem> getItemMapper() {
        return itemMapper;
    }
    
    public static Map<Integer, InteractiveItem> getInteractiveItems() {
        return interactiveItems;
    }

    /**
     * Important to call in onEnable to register the GUI listener.
     *
     * @param plugin The plugin instance.
     */
    public static void register(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(new GUIClickListener(), plugin);
    }
    
    /**
     * Create an inventory with the specified title and size for a player
     * 
     * @param player The player to create the inventory for
     * @param size The size of the inventory
     * @param title The title of the inventory
     * @return The created inventory
     */
    public static Inventory createInventory(Player player, int size, String title) {
        return Bukkit.createInventory(player, size, title);
    }

    /**
     * Fills the inventory with items, while also creating a border around it, with an option to disable sides and keep only the top and bottom frame.
     *
     * @param inventory   The inventory to create the border in.
     * @param fillerPanel The material to use for the inner fill, if null, it won't replace the original contents.
     * @param borderPanel The material to use for the border, if null, it won't replace the original contents. If you want the borders to be the same as filler, you have to specify the same item as in fillerPanel.
     * @param full        Whether to create a full border or just a frame.
     */
    public static void fillInventory(Inventory inventory, @Nullable ItemStack fillerPanel, @Nullable ItemStack borderPanel, boolean full) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if ((i % 9 == 0 || (i - 8) % 9 == 0) && borderPanel != null)
                inventory.setItem(i, borderPanel);
            else if (full && (i < 9 || i >= inventory.getSize() - 9) && borderPanel != null)
                inventory.setItem(i, borderPanel);
            else if (fillerPanel != null)
                inventory.setItem(i, fillerPanel);
        }
    }

    /**
     * Fills the inventory with items, while also creating a border around it.
     *
     * @param inventory   The inventory to create the border in.
     * @param fillerPanel The material to use for the inner fill, if null, it won't replace the original contents.
     * @param borderPanel The material to use for the border, if null, it won't replace the original contents. If you want the borders to be the same as filler, you have to specify the same item as in fillerPanel.
     */
    public static void fillInventory(Inventory inventory, @Nullable ItemStack fillerPanel, @Nullable ItemStack borderPanel) {
        fillInventory(inventory, fillerPanel, borderPanel, true);
    }
    
    /**
     * Clear all interactive items
     */
    public static void clearInteractiveItems() {
        interactiveItems.clear();
    }
}
