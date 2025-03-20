package com.hongminh54.storage.Events;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.Chat;
import com.hongminh54.storage.Utils.File;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * Lớp quản lý sự kiện đào đá nâng cao
 */
public class MiningEvent {
    
    private static MiningEvent instance;
    private boolean active = false;
    private EventType currentEventType = EventType.NONE;
    private final Map<UUID, Integer> playerContributions = new HashMap<>();
    private final Map<String, Double> materialMultipliers = new HashMap<>();
    private int eventDuration = 0;
    private int remainingTime = 0;
    private BukkitTask eventTask = null;
    private BukkitTask reminderTask = null;
    private BukkitTask progressUpdateTask = null;
    
    /**
     * Các loại sự kiện có thể diễn ra
     */
    public enum EventType {
        NONE("Không có sự kiện"),
        DOUBLE_DROP("Nhân đôi tỷ lệ rơi"),
        FORTUNE_BOOST("Tăng cường Fortune"),
        RARE_MATERIALS("Khoáng sản quý hiếm"),
        COMMUNITY_GOAL("Mục tiêu cộng đồng");
        
        private final String displayName;
        
        EventType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return this.displayName;
        }
    }
    
    private MiningEvent() {
        loadEventConfig();
    }
    
    /**
     * Lấy đối tượng singleton của MiningEvent
     * @return instance của MiningEvent
     */
    public static MiningEvent getInstance() {
        if (instance == null) {
            instance = new MiningEvent();
        }
        return instance;
    }
    
    /**
     * Tải cấu hình sự kiện từ file config
     */
    public void loadEventConfig() {
        FileConfiguration config = File.getEvents();
        
        // Tải hệ số nhân cho từng loại vật liệu
        if (config.contains("event.material_multipliers")) {
            for (String key : config.getConfigurationSection("event.material_multipliers").getKeys(false)) {
                double multiplier = config.getDouble("event.material_multipliers." + key);
                materialMultipliers.put(key, multiplier);
            }
        }
    }
    
    /**
     * Bắt đầu một sự kiện ngẫu nhiên
     * @param duration Thời gian diễn ra sự kiện (giây)
     */
    public void startRandomEvent(int duration) {
        // Không bắt đầu sự kiện mới nếu đang có một sự kiện diễn ra
        if (isActive()) return;
        
        // Chọn ngẫu nhiên một loại sự kiện từ các loại đã định nghĩa
        EventType[] types = EventType.values();
        currentEventType = types[ThreadLocalRandom.current().nextInt(1, types.length)];
        
        startEvent(currentEventType, duration);
    }
    
    /**
     * Bắt đầu một sự kiện cụ thể
     * @param type Loại sự kiện
     * @param duration Thời gian diễn ra (giây)
     */
    public void startEvent(EventType type, int duration) {
        // Không bắt đầu sự kiện mới nếu đang có một sự kiện diễn ra
        if (isActive()) return;
        
        active = true;
        currentEventType = type;
        eventDuration = duration;
        remainingTime = duration;
        
        // Xóa dữ liệu đóng góp cũ nếu có
        playerContributions.clear();
        
        // Thông báo sự kiện bắt đầu
        broadcastEventStart();
        
        // Lập lịch kết thúc sự kiện
        eventTask = new BukkitRunnable() {
            @Override
            public void run() {
                remainingTime--;
                
                if (remainingTime <= 0) {
                    endEvent();
                    this.cancel();
                }
            }
        }.runTaskTimer(Storage.getStorage(), 20L, 20L);
        
        // Lập lịch nhắc nhở về sự kiện
        long reminderInterval = Math.min(duration, 300) / 3 * 20L; // Chia thời gian thành 3 phần để nhắc nhở
        reminderTask = new BukkitRunnable() {
            @Override
            public void run() {
                broadcastEventReminder();
            }
        }.runTaskTimer(Storage.getStorage(), reminderInterval, reminderInterval);
        
        // Nếu là sự kiện cộng đồng và cập nhật tiến trình được bật
        if (type == EventType.COMMUNITY_GOAL && 
            File.getEvents().getBoolean("event.community_goal.progress_display.enable", true)) {
            int updateInterval = File.getEvents().getInt("event.community_goal.progress_display.update_interval", 300);
            progressUpdateTask = new BukkitRunnable() {
                @Override
                public void run() {
                    broadcastProgressUpdate();
                }
            }.runTaskTimer(Storage.getStorage(), 20 * 60L, 20L * updateInterval); // Cập nhật sau 1 phút và định kỳ
        }
    }
    
    /**
     * Kết thúc sự kiện hiện tại
     */
    public void endEvent() {
        if (!isActive()) return;
        
        active = false;
        
        // Thông báo sự kiện kết thúc
        broadcastEventEnd();
        
        // Trao thưởng nếu là sự kiện mục tiêu cộng đồng
        if (currentEventType == EventType.COMMUNITY_GOAL) {
            distributeRewards();
        }
        
        // Hủy các task đang chạy
        if (eventTask != null && !eventTask.isCancelled()) {
            eventTask.cancel();
            eventTask = null;
        }
        
        if (reminderTask != null && !reminderTask.isCancelled()) {
            reminderTask.cancel();
            reminderTask = null;
        }
        
        if (progressUpdateTask != null && !progressUpdateTask.isCancelled()) {
            progressUpdateTask.cancel();
            progressUpdateTask = null;
        }
        
        // Đặt lại loại sự kiện
        currentEventType = EventType.NONE;
        remainingTime = 0;
    }
    
    /**
     * Gửi thông báo về việc bắt đầu sự kiện
     */
    private void broadcastEventStart() {
        String announceMessage = Chat.colorize(File.getEvents().getString("event.messages.start")
                .replace("%event_type%", currentEventType.getDisplayName())
                .replace("%duration%", formatTime(eventDuration)));
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(announceMessage);
        }
        
        // Thông báo tiêu đề nếu được cấu hình
        if (File.getEvents().getBoolean("event.title.enable")) {
            String title = Chat.colorize(File.getEvents().getString("event.title.start_title")
                    .replace("%event_type%", currentEventType.getDisplayName()));
            String subtitle = Chat.colorize(File.getEvents().getString("event.title.start_subtitle")
                    .replace("%duration%", formatTime(eventDuration)));
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendTitle(title, subtitle, 10, 70, 20);
            }
        }
    }
    
    /**
     * Gửi thông báo nhắc nhở về sự kiện đang diễn ra
     */
    private void broadcastEventReminder() {
        if (!isActive()) return;
        
        String reminderMessage = Chat.colorize(File.getEvents().getString("event.messages.reminder")
                .replace("%event_type%", currentEventType.getDisplayName())
                .replace("%remaining%", formatTime(remainingTime)));
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(reminderMessage);
        }
    }
    
    /**
     * Gửi thông báo về việc kết thúc sự kiện
     */
    private void broadcastEventEnd() {
        String endMessage = Chat.colorize(File.getEvents().getString("event.messages.end")
                .replace("%event_type%", currentEventType.getDisplayName()));
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(endMessage);
        }
        
        // Thông báo tiêu đề nếu được cấu hình
        if (File.getEvents().getBoolean("event.title.enable")) {
            String title = Chat.colorize(File.getEvents().getString("event.title.end_title")
                    .replace("%event_type%", currentEventType.getDisplayName()));
            String subtitle = Chat.colorize(File.getEvents().getString("event.title.end_subtitle"));
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendTitle(title, subtitle, 10, 70, 20);
            }
        }
    }
    
    /**
     * Phân phối phần thưởng cho người chơi tham gia sự kiện mục tiêu cộng đồng
     */
    private void distributeRewards() {
        int totalContribution = playerContributions.values().stream().mapToInt(Integer::intValue).sum();
        int targetGoal = File.getEvents().getInt("event.community_goal.target", 1000);
        
        // Kiểm tra xem mục tiêu có đạt được không
        if (totalContribution >= targetGoal) {
            // Phân phối phần thưởng cho tất cả người chơi đã đóng góp
            for (UUID playerUUID : playerContributions.keySet()) {
                Player player = Bukkit.getPlayer(playerUUID);
                if (player != null && player.isOnline()) {
                    // Tính toán phần thưởng dựa trên đóng góp của người chơi
                    int contribution = playerContributions.get(playerUUID);
                    double contributionPercentage = (double) contribution / totalContribution;
                    
                    // Áp dụng phần thưởng
                    applyReward(player, contributionPercentage);
                    
                    // Thông báo
                    String rewardMessage = Chat.colorize(File.getEvents().getString("event.messages.reward")
                            .replace("%contribution%", String.valueOf(contribution))
                            .replace("%percentage%", String.format("%.1f", contributionPercentage * 100)));
                    player.sendMessage(rewardMessage);
                }
            }
            
            // Thông báo chung về việc đạt mục tiêu
            String goalReachedMessage = Chat.colorize(File.getEvents().getString("event.messages.goal_reached")
                    .replace("%total%", String.valueOf(totalContribution))
                    .replace("%target%", String.valueOf(targetGoal)));
            Bukkit.broadcastMessage(goalReachedMessage);
        } else {
            // Thông báo về việc không đạt mục tiêu
            String goalFailedMessage = Chat.colorize(File.getEvents().getString("event.messages.goal_failed")
                    .replace("%total%", String.valueOf(totalContribution))
                    .replace("%target%", String.valueOf(targetGoal)));
            Bukkit.broadcastMessage(goalFailedMessage);
        }
    }
    
    /**
     * Áp dụng phần thưởng cho người chơi dựa trên tỷ lệ đóng góp
     */
    private void applyReward(Player player, double contributionPercentage) {
        FileConfiguration config = File.getEvents();
        
        // Phần thưởng vật phẩm
        if (config.getBoolean("event.rewards.items.enable")) {
            for (String itemKey : config.getConfigurationSection("event.rewards.items.list").getKeys(false)) {
                String materialName = config.getString("event.rewards.items.list." + itemKey + ".material");
                int amount = config.getInt("event.rewards.items.list." + itemKey + ".amount");
                
                // Điều chỉnh số lượng dựa trên đóng góp
                int adjustedAmount = (int) Math.ceil(amount * contributionPercentage);
                
                if (adjustedAmount > 0) {
                    try {
                        Material material = Material.valueOf(materialName);
                        ItemStack reward = new ItemStack(material, adjustedAmount);
                        
                        // Kiểm tra xem túi đồ có đủ chỗ không
                        if (player.getInventory().firstEmpty() != -1) {
                            player.getInventory().addItem(reward);
                        } else {
                            // Thả xuống đất nếu túi đồ đầy
                            player.getWorld().dropItemNaturally(player.getLocation(), reward);
                            player.sendMessage(Chat.colorize(config.getString("event.messages.inventory_full")));
                        }
                    } catch (IllegalArgumentException e) {
                        Storage.getStorage().getLogger().warning("Không thể tạo vật phẩm phần thưởng: " + materialName);
                    }
                }
            }
        }
        
        // Phần thưởng lệnh
        if (config.getBoolean("event.rewards.commands.enable")) {
            for (String command : config.getStringList("event.rewards.commands.list")) {
                String processedCommand = command.replace("%player%", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
            }
        }
    }
    
    /**
     * Kiểm tra xem sự kiện có đang diễn ra không
     * @return true nếu có sự kiện đang diễn ra
     */
    public boolean isActive() {
        return active;
    }
    
    /**
     * Lấy loại sự kiện hiện tại
     * @return loại sự kiện đang diễn ra
     */
    public EventType getCurrentEventType() {
        return currentEventType;
    }
    
    /**
     * Lấy thời gian còn lại của sự kiện (giây)
     * @return thời gian còn lại
     */
    public int getRemainingTime() {
        return remainingTime;
    }
    
    /**
     * Lấy tổng thời gian của sự kiện (giây)
     * @return tổng thời gian
     */
    public int getEventDuration() {
        return eventDuration;
    }
    
    /**
     * Lấy hệ số nhân cho một loại vật liệu cụ thể
     * @param material Tên vật liệu
     * @return hệ số nhân, mặc định là 1.0
     */
    public double getMaterialMultiplier(String material) {
        return materialMultipliers.getOrDefault(material, 1.0);
    }
    
    /**
     * Tạo thanh tiến trình dạng văn bản cho sự kiện cộng đồng
     * @param current Tiến trình hiện tại
     * @param target Mục tiêu cần đạt
     * @return Thanh tiến trình dạng văn bản
     */
    private String createProgressBar(int current, int target) {
        int length = File.getEvents().getInt("event.community_goal.progress_display.progress_bar.length", 30);
        String filledChar = File.getEvents().getString("event.community_goal.progress_display.progress_bar.filled_char", "|");
        String emptyChar = File.getEvents().getString("event.community_goal.progress_display.progress_bar.empty_char", ".");
        String filledColor = File.getEvents().getString("event.community_goal.progress_display.progress_bar.filled_color", "&a");
        String emptyColor = File.getEvents().getString("event.community_goal.progress_display.progress_bar.empty_color", "&7");
        
        double percentage = Math.min(1.0, (double) current / target);
        int filledLength = (int) Math.floor(length * percentage);
        int emptyLength = length - filledLength;
        
        StringBuilder bar = new StringBuilder();
        
        // Phần đã hoàn thành
        if (filledLength > 0) {
            bar.append(filledColor);
            for (int i = 0; i < filledLength; i++) {
                bar.append(filledChar);
            }
        }
        
        // Phần chưa hoàn thành
        if (emptyLength > 0) {
            bar.append(emptyColor);
            for (int i = 0; i < emptyLength; i++) {
                bar.append(emptyChar);
            }
        }
        
        return bar.toString();
    }
    
    /**
     * Gửi thông báo cập nhật tiến trình cho tất cả người chơi
     */
    private void broadcastProgressUpdate() {
        if (!isActive() || currentEventType != EventType.COMMUNITY_GOAL) return;
        
        int totalContribution = playerContributions.values().stream().mapToInt(Integer::intValue).sum();
        int targetGoal = File.getEvents().getInt("event.community_goal.target", 1000);
        double percentage = (double) totalContribution / targetGoal * 100;
        
        // Tạo thanh tiến trình
        String progressBar = createProgressBar(totalContribution, targetGoal);
        
        // Gửi thông báo cập nhật tiến trình
        String updateMessage = Chat.colorize(File.getEvents().getString("event.community_goal.progress_display.update_message", 
                "&6&lCẬP NHẬT TIẾN TRÌNH: &f%bar% &7(%percentage%%)")
                .replace("%bar%", progressBar)
                .replace("%percentage%", String.format("%.1f", percentage))
                .replace("%current%", String.valueOf(totalContribution))
                .replace("%target%", String.valueOf(targetGoal)));
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(updateMessage);
        }
    }
    
    /**
     * Xử lý khi người chơi phá vỡ một khối trong sự kiện
     * @param player Người chơi
     * @param material Vật liệu của khối
     * @param amount Số lượng
     * @return Số lượng sau khi áp dụng hiệu ứng sự kiện
     */
    public int processBlockBreak(Player player, String material, int amount) {
        if (!isActive()) return amount;
        
        int result = amount;
        
        switch (currentEventType) {
            case DOUBLE_DROP:
                // Nhân đôi số lượng vật phẩm
                result = amount * 2;
                break;
                
            case FORTUNE_BOOST:
                // Tăng số lượng vật phẩm theo hệ số ngẫu nhiên (1.5x - 3x)
                double fortuneMultiplier = 1.5 + ThreadLocalRandom.current().nextDouble(1.5);
                result = (int) Math.ceil(amount * fortuneMultiplier);
                break;
                
            case RARE_MATERIALS:
                // Sử dụng hệ số nhân cụ thể cho từng loại vật liệu
                double materialMultiplier = getMaterialMultiplier(material);
                result = (int) Math.ceil(amount * materialMultiplier);
                break;
                
            case COMMUNITY_GOAL:
                // Ghi nhận đóng góp của người chơi
                UUID playerUUID = player.getUniqueId();
                playerContributions.put(playerUUID, playerContributions.getOrDefault(playerUUID, 0) + amount);
                
                // Cập nhật tiến độ cộng đồng nếu được cấu hình
                if (File.getEvents().getBoolean("event.community_goal.show_progress")) {
                    int totalContribution = playerContributions.values().stream().mapToInt(Integer::intValue).sum();
                    int targetGoal = File.getEvents().getInt("event.community_goal.target", 1000);
                    int playerContribution = playerContributions.get(playerUUID);
                    
                    boolean showInChat = File.getEvents().getBoolean("event.community_goal.progress_display.show_in_chat", true);
                    boolean showInActionbar = File.getEvents().getBoolean("event.community_goal.progress_display.show_in_actionbar", true);
                    double percentage = (double) totalContribution / targetGoal * 100;
                    
                    // Chỉ hiển thị tiến độ cá nhân khi đóng góp đạt mốc 50
                    if (playerContribution % 50 == 0 && showInChat) {
                        String progressMessage = Chat.colorize(File.getEvents().getString("event.messages.progress")
                                .replace("%contribution%", String.valueOf(playerContribution))
                                .replace("%total%", String.valueOf(totalContribution))
                                .replace("%target%", String.valueOf(targetGoal))
                                .replace("%percentage%", String.format("%.1f", percentage)));
                        player.sendMessage(progressMessage);
                    }
                    
                    // Hiển thị thanh tiến trình trên action bar
                    if (showInActionbar) {
                        String progressBar = createProgressBar(totalContribution, targetGoal);
                        String actionBarMessage = Chat.colorize("&e" + currentEventType.getDisplayName() + ": " + progressBar + 
                                " &f" + totalContribution + "&7/&f" + targetGoal + " &7(&e" + String.format("%.1f", percentage) + "%&7)");
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionBarMessage));
                    }
                }
                break;
                
            default:
                break;
        }
        
        return result;
    }
    
    /**
     * Định dạng thời gian từ giây sang "MM:SS"
     * @param seconds Số giây
     * @return Chuỗi thời gian định dạng
     */
    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%02d:%02d", minutes, remainingSeconds);
    }
    
    /**
     * Lấy tổng đóng góp hiện tại cho sự kiện cộng đồng
     * @return tổng đóng góp
     */
    public int getTotalContribution() {
        if (currentEventType != EventType.COMMUNITY_GOAL) return 0;
        return playerContributions.values().stream().mapToInt(Integer::intValue).sum();
    }
    
    /**
     * Lấy mục tiêu cộng đồng từ cấu hình
     * @return mục tiêu cần đạt
     */
    public int getCommunityGoalTarget() {
        return File.getEvents().getInt("event.community_goal.target", 1000);
    }
    
    /**
     * Lấy phần trăm hoàn thành mục tiêu
     * @return phần trăm hoàn thành
     */
    public double getCompletionPercentage() {
        if (currentEventType != EventType.COMMUNITY_GOAL) return 0.0;
        int total = getTotalContribution();
        int target = getCommunityGoalTarget();
        return (double) total / target * 100;
    }
} 