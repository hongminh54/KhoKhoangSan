package com.hongminh54.storage.Listeners;

import com.hongminh54.storage.Action.Deposit;
import com.hongminh54.storage.Action.Sell;
import com.hongminh54.storage.Action.Withdraw;
import com.hongminh54.storage.Utils.File;
import com.hongminh54.storage.Utils.Number;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Objects;

public class Chat implements Listener {
    public static HashMap<Player, String> chat_deposit = new HashMap<>();
    public static HashMap<Player, String> chat_withdraw = new HashMap<>();
    public static HashMap<Player, String> chat_sell = new HashMap<>();

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onChat(@NotNull AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        String message = ChatColor.stripColor(e.getMessage());
        if (chat_deposit.containsKey(p) && chat_deposit.get(p) != null) {
            if (Number.getInteger(message) > 0) {
                new Deposit(p, chat_deposit.get(p), (long) Number.getInteger(message)).doAction();
            } else {
                p.sendMessage(com.hongminh54.storage.Utils.Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.unknown_number"))
                        .replace("<number>", message)));
            }
            chat_deposit.remove(p);
            e.setCancelled(true);
        }
        if (chat_withdraw.containsKey(p) && chat_withdraw.get(p) != null) {
            if (Number.getInteger(message) > 0) {
                new Withdraw(p, chat_withdraw.get(p), Number.getInteger(message)).doAction();
            } else {
                p.sendMessage(com.hongminh54.storage.Utils.Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.unknown_number"))
                        .replace("<number>", message)));
            }
            chat_withdraw.remove(p);
            e.setCancelled(true);
        }
        if (chat_sell.containsKey(p) && chat_sell.get(p) != null) {
            if (Number.getInteger(message) > 0) {
                new Sell(p, chat_sell.get(p), Number.getInteger(message)).doAction();
            } else {
                p.sendMessage(com.hongminh54.storage.Utils.Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.unknown_number"))
                        .replace("<number>", message)));
            }
            chat_sell.remove(p);
            e.setCancelled(true);
        }
    }
}
