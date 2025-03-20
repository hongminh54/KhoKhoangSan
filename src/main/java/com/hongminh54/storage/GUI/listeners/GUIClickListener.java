package com.hongminh54.storage.GUI.listeners;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import com.hongminh54.storage.GUI.GUI;
import com.hongminh54.storage.GUI.manager.IGUI;

import de.tr7zw.changeme.nbtapi.NBTItem;

public class GUIClickListener implements Listener {
    private static final HashMap<UUID, Long> interactTimeout = new HashMap<>();

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player))
            return;

        Player player = (Player) e.getWhoClicked();

        // Kiểm tra xem top inventory có phải là IGUI không
        boolean isGUIHolder = e.getView().getTopInventory() != null && 
                            e.getView().getTopInventory().getHolder() != null && 
                            e.getView().getTopInventory().getHolder() instanceof IGUI;
        
        // Nếu đang click vào GUI, hủy tất cả sự kiện click và xử lý item tương tác
        if (isGUIHolder) {
            // Hủy tất cả sự kiện click, kể cả click vào bottom inventory
            e.setCancelled(true);
            player.updateInventory();
            
            // Chỉ xử lý click vào top inventory (GUI) và có item
            if (e.getClickedInventory() == e.getView().getTopInventory() && 
                e.getCurrentItem() != null && 
                e.getCurrentItem().getType() != Material.AIR) {
                
                try {
                    NBTItem nbtItem = new NBTItem(e.getCurrentItem());
                    if (nbtItem.hasTag("storage:id")) {
                        UUID uuid = nbtItem.getUUID("storage:id");
                        if (uuid != null && GUI.getItemMapper().containsKey(uuid)) {
                            GUI.getItemMapper().get(uuid).handleClick(player, e.getClick());
                        }
                    }
                } catch (Exception ex) {
                    // Bỏ qua lỗi, không gây crash server
                }
            }
        } else {
            // Nếu không phải GUI, chỉ kiểm tra và xử lý các item có NBT tag
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() != Material.AIR) {
                try {
                    NBTItem nbtItem = new NBTItem(e.getCurrentItem());
                    if (nbtItem.hasTag("storage:id")) {
                        e.setCancelled(true);
                        player.updateInventory();
                        
                        UUID uuid = nbtItem.getUUID("storage:id");
                        if (uuid != null && GUI.getItemMapper().containsKey(uuid)) {
                            GUI.getItemMapper().get(uuid).handleClick(player, e.getClick());
                        }
                    }
                } catch (Exception ex) {
                    // Bỏ qua lỗi, không gây crash server
                }
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getItem() == null || e.getItem().getType() == Material.AIR) 
            return;
            
        try {
            NBTItem nbtItem = new NBTItem(e.getItem());
            if (!nbtItem.hasTag("storage:id")) 
                return;
                
            UUID uuid = nbtItem.getUUID("storage:id");
            if (uuid != null && GUI.getItemMapper().containsKey(uuid) && 
                System.currentTimeMillis() >= interactTimeout.getOrDefault(e.getPlayer().getUniqueId(), -1L)) {
                GUI.getItemMapper().get(uuid).handleClick(e.getPlayer(), e.getAction());
                interactTimeout.put(e.getPlayer().getUniqueId(), System.currentTimeMillis() + 100L); // Timeout prevents the GUI opening twice
            }
        } catch (Exception ex) {
            // Bỏ qua lỗi, không gây crash server
        }

        e.setCancelled(true);
    }

    @EventHandler
    public void onAnimation(PlayerAnimationEvent e) { // This compensates for the fact that PlayerInteractEvent is not called when the player is in Adventure mode
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING || 
            e.getPlayer().getTargetBlock(new HashSet<>(), 5).getType() == Material.AIR || 
            e.getPlayer().getGameMode() != GameMode.ADVENTURE)
            return;

        ItemStack item = e.getPlayer().getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) 
            return;
            
        try {
            NBTItem nbtItem = new NBTItem(item);
            if (!nbtItem.hasTag("storage:id")) 
                return;
                
            UUID uuid = nbtItem.getUUID("storage:id");
            if (uuid != null && GUI.getItemMapper().containsKey(uuid) && 
                System.currentTimeMillis() >= interactTimeout.getOrDefault(e.getPlayer().getUniqueId(), -1L)) {
                GUI.getItemMapper().get(uuid).handleClick(e.getPlayer(), Action.RIGHT_CLICK_BLOCK);
                interactTimeout.put(e.getPlayer().getUniqueId(), System.currentTimeMillis() + 100L); // Timeout prevents the GUI opening twice
            }
        } catch (Exception ex) {
            // Bỏ qua lỗi, không gây crash server
        }
        
        e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (e.getItemDrop() == null || e.getItemDrop().getItemStack() == null || 
            e.getItemDrop().getItemStack().getType() == Material.AIR)
            return;
            
        try {
            NBTItem nbtItem = new NBTItem(e.getItemDrop().getItemStack());
            if (nbtItem.hasTag("storage:id")) {
                e.setCancelled(true);
            }
        } catch (Exception ex) {
            // Bỏ qua lỗi, không gây crash server
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (e.getDrops() == null || e.getDrops().isEmpty())
            return;
            
        e.getDrops().removeIf(item -> {
            if (item == null || item.getType() == Material.AIR)
                return false;
                
            try {
                NBTItem nbtItem = new NBTItem(item);
                return nbtItem.hasTag("storage:id");
            } catch (Exception ex) {
                return false;
            }
        });
    }
}