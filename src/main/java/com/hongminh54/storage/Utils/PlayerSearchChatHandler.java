package com.hongminh54.storage.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.hongminh54.storage.Storage;

/**
 * Quản lý nhập liệu từ chat của người chơi
 */
public class PlayerSearchChatHandler {
    private static final Map<UUID, ChatInputListener> activeInputs = new HashMap<>();
    // Thêm cooldown để ngăn chặn việc đăng ký listener quá nhanh
    private static final Map<UUID, Long> lastInputTime = new HashMap<>();
    private static final long INPUT_COOLDOWN = 500; // 0.5 giây cooldown
    
    /**
     * Bắt đầu quá trình nhập liệu từ chat
     * @param player Người chơi sẽ nhập liệu
     * @param prompt Tin nhắn yêu cầu người chơi nhập liệu
     * @param callback Callback khi người chơi nhập liệu xong
     */
    public static void startChatInput(Player player, String prompt, Consumer<String> callback) {
        if (player == null || callback == null || !player.isOnline()) {
            return;
        }
        
        UUID playerId = player.getUniqueId();
        
        // Kiểm tra cooldown để tránh spam
        long currentTime = System.currentTimeMillis();
        if (lastInputTime.containsKey(playerId)) {
            long lastTime = lastInputTime.get(playerId);
            if (currentTime - lastTime < INPUT_COOLDOWN) {
                // Vẫn trong thời gian cooldown, không cho phép đăng ký mới
                return;
            }
        }
        
        // Cập nhật thời gian input cuối cùng
        lastInputTime.put(playerId, currentTime);
        
        // Hủy tất cả input cũ trước khi đăng ký mới
        cancelInput(player);
        
        // Đăng ký input mới sau 3 tick để đảm bảo listener cũ đã bị hủy hoàn toàn
        Bukkit.getScheduler().runTaskLater(Storage.getStorage(), () -> {
            if (player.isOnline()) {
                registerNewInput(player, prompt, callback);
            }
        }, 3L);
    }
    
    /**
     * Đăng ký input mới sau khi đã hủy input cũ
     * @param player Người chơi
     * @param prompt Prompt hiển thị
     * @param callback Callback khi người chơi nhập
     */
    private static void registerNewInput(Player player, String prompt, Consumer<String> callback) {
        if (!player.isOnline()) return;
        
        try {
            // Kiểm tra một lần nữa xem có input đang hoạt động không
            UUID playerId = player.getUniqueId();
            if (activeInputs.containsKey(playerId)) {
                HandlerList.unregisterAll(activeInputs.get(playerId));
                activeInputs.remove(playerId);
            }
            
            // Hiển thị prompt cho người chơi với màu sắc rõ ràng hơn
            player.sendMessage(Chat.colorize("&8&m------------------------------"));
            player.sendMessage(Chat.colorize("&b&l➤ " + prompt));
            player.sendMessage(Chat.colorize("&8&l┃ &f• &eNhập tên người chơi (ví dụ: &aTYBZI&e, &aAnhBaToCom&e)"));
            player.sendMessage(Chat.colorize("&8&l┃ &f• &eChỉ cần gõ một phần tên cũng được (ví dụ: gõ &ast &ethay vì &aSteve&e)"));
            player.sendMessage(Chat.colorize("&8&l┃ &f• &eGõ &a* &etrước tên để tìm chính xác (ví dụ: &a*Steve&e)"));
            player.sendMessage(Chat.colorize("&8&l┃ &f• &7Hệ thống sẽ tự động tìm người chơi phù hợp"));
            player.sendMessage(Chat.colorize("&8&l┃ &f• &cGõ &c'huy' &choặc &c'cancel' &cđể quay lại"));
            player.sendMessage(Chat.colorize("&8&m------------------------------"));
            
            // Phát hiệu ứng âm thanh
            SoundManager.playSound(player, "BLOCK_NOTE_BLOCK_PLING", 0.6f, 1.2f);
            
            // Tạo và đăng ký listener mới
            ChatInputListener listener = new ChatInputListener(player, callback);
            activeInputs.put(playerId, listener);
            
            // Đăng ký listener
            Bukkit.getPluginManager().registerEvents(listener, Storage.getStorage());
            
            // Debug log
            if (Storage.getStorage().isDebug()) {
                Storage.getStorage().getLogger().info("Đã đăng ký listener chat cho: " + player.getName());
            }
        } catch (Exception e) {
            // Ghi log lỗi
            Storage.getStorage().getLogger().severe("Lỗi khi đăng ký input chat: " + e.getMessage());
            
            // Thông báo lỗi cho người chơi
            player.sendMessage(Chat.colorize("&8[&4&l✕&8] &cCó lỗi xảy ra khi xử lý yêu cầu của bạn."));
        }
    }
    
    /**
     * Hủy quá trình nhập liệu của người chơi
     * @param player Người chơi
     */
    public static void cancelInput(Player player) {
        if (player == null) return;
        
        UUID playerId = player.getUniqueId();
        if (activeInputs.containsKey(playerId)) {
            try {
                // Lấy listener hiện tại
                ChatInputListener listener = activeInputs.get(playerId);
                
                // Hủy đăng ký listener
                HandlerList.unregisterAll(listener);
                
                // Xóa khỏi map
                activeInputs.remove(playerId);
                
                if (player.isOnline()) {
                    // Thông báo cho người chơi
                    player.sendMessage(Chat.colorize("&8[&c&l✕&8] &cĐã hủy thao tác tìm kiếm."));
                    
                    // Phát hiệu ứng âm thanh
                    SoundManager.playSound(player, "ENTITY_VILLAGER_NO", 0.5f, 0.8f);
                    
                    // Mở lại GUI tìm kiếm người chơi sau khi hủy
                    Bukkit.getScheduler().runTaskLater(Storage.getStorage(), () -> {
                        if (player.isOnline()) {
                            player.performCommand("kho chuyenore");
                        }
                    }, 5L);
                }
                
                // Debug log
                if (Storage.getStorage().isDebug()) {
                    Storage.getStorage().getLogger().info("Đã hủy chat input cho: " + player.getName());
                }
            } catch (Exception e) {
                // Ghi log lỗi
                Storage.getStorage().getLogger().severe("Lỗi khi hủy input chat: " + e.getMessage());
            }
        }
    }
    
    /**
     * Hủy tất cả input đang hoạt động (dùng khi plugin tắt)
     */
    public static void cancelAllInputs() {
        for (ChatInputListener listener : activeInputs.values()) {
            try {
                HandlerList.unregisterAll(listener);
            } catch (Exception e) {
                // Bỏ qua lỗi khi unregister
            }
        }
        activeInputs.clear();
        lastInputTime.clear();
    }
    
    /**
     * Listener xử lý sự kiện chat của người chơi
     */
    private static class ChatInputListener implements Listener {
        private final Player player;
        private final Consumer<String> callback;
        private boolean processed = false;
        
        public ChatInputListener(Player player, Consumer<String> callback) {
            this.player = player;
            this.callback = callback;
        }
        
        @EventHandler(priority = EventPriority.LOWEST)
        public void onPlayerChat(AsyncPlayerChatEvent event) {
            Player chatPlayer = event.getPlayer();
            
            // Chỉ quan tâm đến người chơi đang nhập liệu
            if (!chatPlayer.getUniqueId().equals(player.getUniqueId())) return;
            
            // Kiểm tra trạng thái đã xử lý
            if (processed) return;
            
            // Đánh dấu đã xử lý để tránh callback nhiều lần
            processed = true;
            
            // Hủy sự kiện chat để không hiển thị tin nhắn cho người chơi khác
            event.setCancelled(true);
            
            // Lấy nội dung nhập vào
            String input = event.getMessage().trim();
            
            // Kiểm tra nếu người chơi muốn hủy
            if (input.equalsIgnoreCase("hủy") || input.equalsIgnoreCase("cancel") || 
                input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("huy")) {
                
                // Chạy đồng bộ để tránh lỗi
                Bukkit.getScheduler().runTask(Storage.getStorage(), () -> {
                    // Mở lại GUI tìm kiếm người chơi ban đầu
                    if (player.isOnline()) {
                        // Hủy nhập liệu hiện tại
                        PlayerSearchChatHandler.cancelInput(player);
                    }
                });
                return;
            }
            
            // Biến để lưu input cuối cùng
            final String finalInput;
            
            // Kiểm tra tìm kiếm tự động hoàn thành
            if (!input.startsWith("*") && input.length() >= 2) {
                // Người chơi nhập một phần tên, tìm kiếm người chơi phù hợp nhất
                String partialName = input.toLowerCase();
                Player bestMatch = null;
                
                // Cải thiện thuật toán tìm kiếm
                Map<Player, Integer> matchScores = new HashMap<>();
                
                // Thu thập danh sách người chơi online một lần
                List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
                
                // Giai đoạn 1: Ưu tiên tìm kiếm player khớp chính xác hoặc bắt đầu bằng
                for (Player onlinePlayer : onlinePlayers) {
                    String onlineName = onlinePlayer.getName().toLowerCase();
                    
                    // Khớp chính xác (ưu tiên cao nhất)
                    if (onlineName.equals(partialName)) {
                        bestMatch = onlinePlayer;
                        break; // Đã tìm thấy khớp hoàn hảo, thoát vòng lặp
                    }
                    
                    // Khớp bắt đầu bằng (ưu tiên thứ hai)
                    if (onlineName.startsWith(partialName)) {
                        int score = 1000 - (onlineName.length() - partialName.length());
                        matchScores.put(onlinePlayer, score);
                    }
                    // Khớp chứa (ưu tiên thấp hơn)
                    else if (onlineName.contains(partialName)) {
                        int score = 500 - onlineName.indexOf(partialName);
                        matchScores.put(onlinePlayer, score);
                    }
                }
                
                // Nếu không tìm thấy khớp hoàn hảo, tìm người chơi có điểm cao nhất
                if (bestMatch == null && !matchScores.isEmpty()) {
                    // Tạo biến mới để lưu trữ người chơi có điểm cao nhất
                    Player topPlayer = null;
                    int topScore = -1;
                    
                    for (Map.Entry<Player, Integer> entry : matchScores.entrySet()) {
                        if (entry.getValue() > topScore) {
                            topScore = entry.getValue();
                            topPlayer = entry.getKey();
                        }
                    }
                    
                    bestMatch = topPlayer;
                }
                
                // Nếu tìm thấy người chơi phù hợp
                if (bestMatch != null) {
                    // Sử dụng tên đầy đủ của người chơi phù hợp nhất
                    finalInput = bestMatch.getName();
                    
                    // Thông báo cho người chơi về việc tự động hoàn thành
                    if (!finalInput.equalsIgnoreCase(input)) {
                        final Player finalBestMatch = bestMatch; // cần biến final cho lambda
                        
                        Bukkit.getScheduler().runTask(Storage.getStorage(), () -> {
                            if (player.isOnline()) {
                                player.sendMessage(Chat.colorize("&8[&a&l✓&8] &aĐã tự động hoàn thành tên thành: &e" + finalBestMatch.getName()));
                                SoundManager.playSound(player, "BLOCK_NOTE_BLOCK_PLING", 0.5f, 1.5f);
                            }
                        });
                    }
                } else {
                    // Không tìm thấy, sử dụng input ban đầu
                    finalInput = input;
                }
            } else if (input.startsWith("*") && input.length() > 1) {
                // Tìm kiếm chính xác, loại bỏ dấu *
                finalInput = input.substring(1);
                final String displayInput = finalInput; // cần biến final cho lambda
                
                Bukkit.getScheduler().runTask(Storage.getStorage(), () -> {
                    if (player.isOnline()) {
                        player.sendMessage(Chat.colorize("&8[&a&l✓&8] &aTìm kiếm chính xác: &e" + displayInput));
                    }
                });
            } else {
                // Sử dụng input ban đầu
                finalInput = input;
            }
            
            // Hủy đăng ký listener và gọi callback
            Bukkit.getScheduler().runTask(Storage.getStorage(), () -> {
                // Hủy đăng ký listener
                UUID playerId = player.getUniqueId();
                if (activeInputs.containsKey(playerId)) {
                    HandlerList.unregisterAll(this);
                    activeInputs.remove(playerId);
                }
                
                // Kiểm tra nếu người chơi vẫn online
                if (!player.isOnline()) return;
                
                // Đánh dấu đã xử lý
                processed = true;
                
                // Chờ 2 tick trước khi gọi callback để đảm bảo đồng bộ hóa
                Bukkit.getScheduler().runTaskLater(Storage.getStorage(), () -> {
                    if (player.isOnline()) {
                        try {
                            // Gọi callback để xử lý dữ liệu nhập vào
                            callback.accept(finalInput);
                        } catch (Exception e) {
                            // Ghi log lỗi
                            Storage.getStorage().getLogger().severe("Lỗi khi xử lý input chat: " + e.getMessage());
                            
                            // Thông báo lỗi cho người chơi
                            player.sendMessage(Chat.colorize("&8[&4&l✕&8] &cCó lỗi xảy ra trong quá trình xử lý dữ liệu."));
                        }
                    }
                }, 2L);
            });
        }
        
        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent event) {
            // Hủy đăng ký listener khi người chơi thoát game
            if (event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
                HandlerList.unregisterAll(this);
                activeInputs.remove(player.getUniqueId());
            }
        }
    }
} 