package com.hongminh54.storage.Listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.jetbrains.annotations.NotNull;

import com.hongminh54.storage.Action.Deposit;
import com.hongminh54.storage.Action.Sell;
import com.hongminh54.storage.Action.Withdraw;
import com.hongminh54.storage.GUI.TransferGUI;
import com.hongminh54.storage.Manager.MineManager;
import com.hongminh54.storage.Utils.File;
import com.hongminh54.storage.Utils.Number;

public class Chat implements Listener {
    public static HashMap<Player, String> chat_deposit = new HashMap<>();
    public static HashMap<Player, String> chat_withdraw = new HashMap<>();
    public static HashMap<Player, String> chat_sell = new HashMap<>();
    
    // HashMap lưu thông tin chat chuyển tài nguyên (Người nhận, Loại tài nguyên)
    public static HashMap<Player, Map.Entry<Player, String>> chat_transfer = new HashMap<>();

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
        // Xử lý chat chuyển tài nguyên
        if (chat_transfer.containsKey(p) && chat_transfer.get(p) != null) {
            if (Number.getInteger(message) > 0) {
                Map.Entry<Player, String> transferInfo = chat_transfer.get(p);
                Player receiver = transferInfo.getKey();
                String material = transferInfo.getValue();
                
                if (receiver != null && receiver.isOnline()) {
                    // Lấy số lượng hiện có của người chơi
                    int amount = Number.getInteger(message);
                    int currentAmount = MineManager.getPlayerBlock(p, material);
                    String materialName = com.hongminh54.storage.Utils.File.getConfig().getString("items." + material, material != null ? material.split(";")[0] : "unknown");
                    
                    // Kiểm tra xem người chơi có đủ tài nguyên không
                    if (currentAmount < amount) {
                        p.sendMessage(com.hongminh54.storage.Utils.Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.action.transfer.not_enough"))
                                .replace("#material#", materialName)
                                .replace("#amount#", String.valueOf(currentAmount))));
                        
                        // Phát âm thanh thất bại
                        try {
                            String failSoundConfig = File.getConfig().getString("effects.transfer_fail.sound", "ENTITY_VILLAGER_NO:1.0:1.0");
                            String[] soundParts = failSoundConfig.split(":");
                            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundParts[0]);
                            float volume = soundParts.length > 1 ? Float.parseFloat(soundParts[1]) : 1.0f;
                            float pitch = soundParts.length > 2 ? Float.parseFloat(soundParts[2]) : 1.0f;
                            
                            p.playSound(p.getLocation(), sound, volume, pitch);
                        } catch (Exception ex) {
                            // Phát âm thanh cảnh báo
                            try {
                                p.playSound(p.getLocation(), org.bukkit.Sound.valueOf("NOTE_BASS"), 0.5f, 0.8f);
                            } catch (Exception exc) {
                                // Bỏ qua nếu không hỗ trợ
                            }
                        }
                    } else {
                        // Thông báo cho người chơi về việc đang xử lý
                        p.sendMessage(com.hongminh54.storage.Utils.Chat.colorize("&8[&e&l❖&8] &aĐang chuyển &f" + amount + " &a" + materialName + " &fcho &e" + receiver.getName() + "..."));
                        
                        // Tạo GUI và gọi phương thức transferResource với số lượng tùy chỉnh
                        TransferGUI transferGUI = new TransferGUI(p, receiver, material);
                        transferGUI.transferResource(p, receiver, material, amount);
                    }
                } else {
                    p.sendMessage(com.hongminh54.storage.Utils.Chat.colorize("&8[&4&l✕&8] &cNgười chơi không còn trực tuyến!"));
                }
            } else {
                p.sendMessage(com.hongminh54.storage.Utils.Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.unknown_number"))
                        .replace("<number>", message)));
            }
            chat_transfer.remove(p);
            e.setCancelled(true);
        }
    }
}
