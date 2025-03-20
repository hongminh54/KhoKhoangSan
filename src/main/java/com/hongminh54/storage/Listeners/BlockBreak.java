package com.hongminh54.storage.Listeners;

/**
 * Lớp này xử lý sự kiện khi người chơi đào khối trong trường hợp toggle của người chơi là TRUE.
 * Lớp BlockBreakEvent_.java xử lý sự kiện khi toggle của người chơi là FALSE.
 * 
 * Chú ý: Cả hai lớp này KHÔNG được xử lý cùng một sự kiện đào khối vì sẽ gây ra việc
 * tài nguyên được thêm hai lần vào kho của người chơi. Kiểm tra MineManager.toggle
 * đã được thêm trong BlockBreakEvent_ để tránh xung đột này.
 */

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.metadata.MetadataValue;
import org.jetbrains.annotations.NotNull;

import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.messages.ActionBar;
import com.cryptomorin.xseries.messages.Titles;
import com.hongminh54.storage.Events.MiningEvent;
import com.hongminh54.storage.Manager.MineManager;
import com.hongminh54.storage.NMS.NMSAssistant;
import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.Chat;
import com.hongminh54.storage.Utils.File;
import com.hongminh54.storage.Utils.Number;
import com.hongminh54.storage.Utils.StatsManager;
import com.hongminh54.storage.WorldGuard.WorldGuard;

public class BlockBreak implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBreak(@NotNull BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block block = e.getBlock();
        boolean inv_full = (p.getInventory().firstEmpty() == -1);
        if (Storage.isWorldGuardInstalled()) {
            if (!WorldGuard.handleForLocation(p, block.getLocation())) {
                return;
            }
        }
        if (File.getConfig().getBoolean("prevent_rebreak")) {
            if (isPlacedBlock(block)) return;
        }
        if (File.getConfig().contains("blacklist_world")) {
            if (File.getConfig().getStringList("blacklist_world").contains(p.getWorld().getName())) return;
        }
        if (MineManager.isAutoPickup(p)) {
            if (inv_full) {
                int old_data = MineManager.getPlayerBlock(p, MineManager.getDrop(block));
                int max_storage = MineManager.getMaxBlock(p);
                int count = max_storage - old_data;
                for (ItemStack itemStack : p.getInventory().getContents()) {
                    if (itemStack != null) {
                        String drop = MineManager.getItemStackDrop(itemStack);
                        int amount = itemStack.getAmount();
                        if (drop != null) {
                            int new_data = old_data + Math.toIntExact(amount);
                            int min = Math.min(count, Math.toIntExact(amount));
                            int replacement = new_data >= max_storage ? min : amount;
                            if (MineManager.addBlockAmount(p, drop, replacement)) {
                                removeItems(p, itemStack, replacement);
                            }
                        }
                    }
                }
            }
            if (MineManager.checkBreak(block)) {
                String drop = MineManager.getDrop(block);
                int amount;
                ItemStack hand = p.getInventory().getItemInMainHand();
                Enchantment fortune = XEnchantment.FORTUNE.get() != null ? XEnchantment.FORTUNE.get() : Objects.requireNonNull(XEnchantment.of(Enchantment.LOOT_BONUS_BLOCKS).get());
                if (!hand.containsEnchantment(fortune)) {
                    amount = getDropAmount(block);
                } else {
                    if (File.getConfig().getStringList("whitelist_fortune").contains(block.getType().name())) {
                        amount = Number.getRandomInteger(getDropAmount(block), getDropAmount(block) + hand.getEnchantmentLevel(fortune) + 2);
                    } else amount = getDropAmount(block);
                }
                
                // Áp dụng hiệu ứng sự kiện khai thác nếu có
                MiningEvent miningEvent = MiningEvent.getInstance();
                if (miningEvent.isActive()) {
                    amount = miningEvent.processBlockBreak(p, drop, amount);
                }
                
                if (MineManager.addBlockAmount(p, drop, amount)) {
                    if (File.getConfig().getBoolean("mine.actionbar.enable")) {
                        String name = File.getConfig().getString("items." + drop);
                        ActionBar.sendActionBar(Storage.getStorage(), p, Chat.colorizewp(Objects.requireNonNull(File.getConfig().getString("mine.actionbar.action")).replace("#item#", name != null ? name : drop.replace("_", " ")).replace("#amount#", String.valueOf(amount)).replace("#storage#", String.valueOf(MineManager.getPlayerBlock(p, drop))).replace("#max#", String.valueOf(MineManager.getMaxBlock(p)))));
                    }
                    if (File.getConfig().getBoolean("mine.title.enable")) {
                        String name = File.getConfig().getString("items." + drop);
                        String replacement = name != null ? name : drop.replace("_", " ");
                        Titles.sendTitle(p, Chat.colorizewp(Objects.requireNonNull(File.getConfig().getString("mine.title.title")).replace("#item#", replacement).replace("#amount#", String.valueOf(amount)).replace("#storage#", String.valueOf(MineManager.getPlayerBlock(p, drop))).replace("#max#", String.valueOf(MineManager.getMaxBlock(p)))), Chat.colorizewp(Objects.requireNonNull(File.getConfig().getString("mine.title.subtitle")).replace("#item#", replacement).replace("#amount#", String.valueOf(amount)).replace("#storage#", String.valueOf(MineManager.getPlayerBlock(p, drop))).replace("#max#", String.valueOf(MineManager.getMaxBlock(p)))));
                    }
                    
                    // Ghi nhận thống kê khai thác - chỉ tính 1 block
                    try {
                        com.hongminh54.storage.Utils.StatsManager.recordMining(p, 1);
                    } catch (Exception ex) {
                        Storage.getStorage().getLogger().log(Level.WARNING, 
                            "Lỗi khi ghi nhận thống kê khai thác cho " + p.getName() + ": " + ex.getMessage(), ex);
                    }
                    
                    if (new NMSAssistant().isVersionGreaterThanOrEqualTo(12)) {
                        e.setDropItems(false);
                    }
                    e.getBlock().getDrops().clear();
                } else {
                    StatsManager.sendStorageFullNotification(p, drop, MineManager.getPlayerBlock(p, drop), MineManager.getMaxBlock(p));
                }
            }
        }
    }

    public void removeItems(Player player, ItemStack itemStack, long amount) {
        final PlayerInventory inv = player.getInventory();
        final ItemStack[] items = inv.getContents();
        int c = 0;
        for (int i = 0; i < items.length; ++i) {
            final ItemStack is = items[i];
            if (is != null) {
                if (itemStack != null) {
                    if (is.isSimilar(itemStack)) {
                        if (c + is.getAmount() > amount) {
                            final long canDelete = amount - c;
                            is.setAmount((int) (is.getAmount() - canDelete));
                            items[i] = is;
                            break;
                        }
                        c += is.getAmount();
                        items[i] = null;
                    }
                }
            }
        }
        inv.setContents(items);
        player.updateInventory();
    }

    private int getDropAmount(Block block) {
        int amount = 0;
        if (block != null) for (ItemStack itemStack : block.getDrops())
            if (itemStack != null) amount += itemStack.getAmount();
        return amount;
    }

    public boolean isPlacedBlock(Block b) {
        List<MetadataValue> metaDataValues = b.getMetadata("PlacedBlock");
        for (MetadataValue value : metaDataValues) {
            return value.asBoolean();
        }
        return false;
    }

}
