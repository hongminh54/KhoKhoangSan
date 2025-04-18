package com.hongminh54.storage.CMD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import com.hongminh54.storage.API.CMDBase;
import com.hongminh54.storage.Database.PlayerData;
import com.hongminh54.storage.Database.TransferHistory;
import com.hongminh54.storage.Events.EventScheduler;
import com.hongminh54.storage.Events.MiningEvent;
import com.hongminh54.storage.GUI.LeaderboardGUI;
import com.hongminh54.storage.GUI.MultiTransferGUI;
import com.hongminh54.storage.GUI.PersonalStorage;
import com.hongminh54.storage.GUI.PlayerSearchGUI;
import com.hongminh54.storage.GUI.StatsGUI;
import com.hongminh54.storage.GUI.TransferGUI;
import com.hongminh54.storage.Manager.ItemManager;
import com.hongminh54.storage.Manager.MineManager;
import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.CacheCoordinator;
import com.hongminh54.storage.Utils.Chat;
import com.hongminh54.storage.Utils.File;
import com.hongminh54.storage.Utils.LeaderboardManager;
import com.hongminh54.storage.Utils.Number;
import com.hongminh54.storage.Utils.SoundManager;
import com.hongminh54.storage.Utils.StatsManager;
import com.hongminh54.storage.Utils.UpdateChecker;
public class StorageCMD extends CMDBase {
    // Biến để theo dõi thời gian sử dụng lệnh
    private static final Map<String, Long> playerCooldowns = new ConcurrentHashMap<>();
    // Biến để theo dõi trạng thái xem toàn bộ log của người chơi (playerUUID -> targetPlayer)
    private static final Map<String, String> viewFullLogPlayers = new HashMap<>();
    // Biến để theo dõi thời gian hết hạn của lệnh logall
    private static final Map<String, Long> logAllExpiryTimes = new HashMap<>();

    public StorageCMD(String name) {
        super(name);
    }

    @Override
    public void execute(@NotNull CommandSender c, String[] args) {
        if (args.length == 0) {
            if (c instanceof Player) {
                try {
                    ((Player) c).openInventory(new PersonalStorage((Player) c).getInventory());
                } catch (IndexOutOfBoundsException e) {
                    c.sendMessage(Chat.colorize(File.getMessage().getString("admin.not_enough_slot")));
                }
            }
        } else if (args[0].equalsIgnoreCase("help")) {
            if (c.hasPermission("storage.admin")) {
                File.getMessage().getStringList("admin.help").forEach(s -> c.sendMessage(Chat.colorize(s)));
            }
            File.getMessage().getStringList("user.help").forEach(s -> c.sendMessage(Chat.colorize(s)));
        } else if (args[0].equalsIgnoreCase("event")) {
            if (c.hasPermission("storage.event")) {
                if (args.length < 2) {
                    c.sendMessage(Chat.colorize(File.getEvents().getString("commands.messages.usage")));
                    return;
                }
                
                MiningEvent miningEvent = MiningEvent.getInstance();
                
                if (args[1].equalsIgnoreCase("start")) {
                    if (miningEvent.isActive()) {
                        c.sendMessage(Chat.colorize("&cĐã có sự kiện đang diễn ra! Hãy dừng sự kiện hiện tại trước khi bắt đầu sự kiện mới."));
                        return;
                    }
                    
                    int duration = File.getEvents().getInt("event.default_duration", 1800);
                    MiningEvent.EventType eventType = MiningEvent.EventType.DOUBLE_DROP;
                    
                    if (args.length >= 3) {
                        try {
                            eventType = MiningEvent.EventType.valueOf(args[2].toUpperCase());
                        } catch (IllegalArgumentException e) {
                            c.sendMessage(Chat.colorize("&cLoại sự kiện không hợp lệ! Các loại sự kiện: DOUBLE_DROP, FORTUNE_BOOST, RARE_MATERIALS, COMMUNITY_GOAL"));
                            return;
                        }
                    }
                    
                    if (args.length >= 4) {
                        try {
                            duration = Integer.parseInt(args[3]);
                        } catch (NumberFormatException e) {
                            c.sendMessage(Chat.colorize("&cThời gian không hợp lệ! Vui lòng nhập một số nguyên dương."));
                            return;
                        }
                    }
                    
                    miningEvent.startEvent(eventType, duration);
                    c.sendMessage(Chat.colorize(File.getEvents().getString("commands.messages.start")
                            .replace("%event_type%", eventType.getDisplayName())
                            .replace("%duration%", String.valueOf(duration))));
                    
                } else if (args[1].equalsIgnoreCase("stop")) {
                    if (!miningEvent.isActive()) {
                        c.sendMessage(Chat.colorize(File.getEvents().getString("commands.messages.no_event")));
                        return;
                    }
                    
                    miningEvent.endEvent();
                    c.sendMessage(Chat.colorize(File.getEvents().getString("commands.messages.stop")));
                    
                } else if (args[1].equalsIgnoreCase("info")) {
                    if (!miningEvent.isActive()) {
                        c.sendMessage(Chat.colorize(File.getEvents().getString("commands.messages.no_event")));
                        return;
                    }
                    
                    c.sendMessage(Chat.colorize("&a&lThông tin sự kiện:"));
                    c.sendMessage(Chat.colorize("&eLoại: &f" + miningEvent.getCurrentEventType().getDisplayName()));
                    c.sendMessage(Chat.colorize("&eThời gian còn lại: &f" + formatTime(miningEvent.getRemainingTime())));
                    c.sendMessage(Chat.colorize("&eThời gian tổng: &f" + formatTime(miningEvent.getEventDuration())));
                    
                    // Hiển thị thông tin chi tiết cho sự kiện cộng đồng
                    if (miningEvent.getCurrentEventType() == MiningEvent.EventType.COMMUNITY_GOAL) {
                        int totalContribution = miningEvent.getTotalContribution();
                        int targetGoal = miningEvent.getCommunityGoalTarget();
                        double percentage = miningEvent.getCompletionPercentage();
                        
                        c.sendMessage(Chat.colorize("&a&lTiến trình mục tiêu cộng đồng:"));
                        c.sendMessage(Chat.colorize("&eTổng đóng góp: &f" + totalContribution + " &7/ &f" + targetGoal));
                        c.sendMessage(Chat.colorize("&eHoàn thành: &f" + String.format("%.1f", percentage) + "%"));
                        
                        // Tạo thanh tiến trình
                        if (c instanceof Player) {
                            Player player = (Player) c;
                            // Sử dụng ký tự cơ bản để thanh tiến trình luôn hiển thị đúng
                            int length = 40; // Dùng một thanh dài hơn cho hiển thị trong chat
                            String filledChar = "|";
                            String emptyChar = ".";
                            String filledColor = "&a";
                            String emptyColor = "&7";
                            
                            double progressPercentage = Math.min(1.0, (double) totalContribution / targetGoal);
                            int filledLength = (int) Math.floor(length * progressPercentage);
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
                            
                            player.sendMessage(Chat.colorize("&e" + bar.toString()));
                        }
                    }
                } else if (args[1].equalsIgnoreCase("reload")) {
                    // Tải lại cấu hình sự kiện và lịch trình
                    File.loadEvents();
                    MiningEvent.getInstance().loadEventConfig();
                    EventScheduler.getInstance().loadSchedules();
                    c.sendMessage(Chat.colorize("&aĐã tải lại cấu hình sự kiện và lịch trình!"));
                } else if (args[1].equalsIgnoreCase("schedule")) {
                    // Hiển thị thông tin lịch trình sự kiện
                    c.sendMessage(Chat.colorize("&a&lLịch trình sự kiện tự động:"));
                    c.sendMessage(Chat.colorize("&eTrạng thái: " + (File.getEvents().getBoolean("scheduler.enable", true) ? "&aBật" : "&cTắt")));
                    c.sendMessage(Chat.colorize("&eKiểm tra: &fMỗi " + File.getEvents().getInt("scheduler.check_interval", 5) + " phút"));
                    c.sendMessage(Chat.colorize("&eCác sự kiện theo lịch:"));
                    
                    ConfigurationSection schedules = File.getEvents().getConfigurationSection("scheduler.schedules");
                    if (schedules != null) {
                        for (String eventId : schedules.getKeys(false)) {
                            String type = schedules.getString(eventId + ".type", "UNKNOWN");
                            int duration = schedules.getInt(eventId + ".duration", 0);
                            int minutes = duration / 60;
                            String days = schedules.getString(eventId + ".days", "ALL");
                            
                            // Hiển thị thông tin sự kiện
                            c.sendMessage(Chat.colorize("  &6" + eventId + ": &f" + type + " &7(Kéo dài " + minutes + " phút)"));
                            
                            // Hiển thị thời gian
                            StringBuilder timeInfo = new StringBuilder("    &eThời gian: &f");
                            if (schedules.contains(eventId + ".time")) {
                                // Định dạng cũ
                                timeInfo.append(schedules.getString(eventId + ".time", "00:00"));
                            } else if (schedules.contains(eventId + ".times")) {
                                // Định dạng mới
                                List<String> times = schedules.getStringList(eventId + ".times");
                                if (!times.isEmpty()) {
                                    for (int i = 0; i < times.size(); i++) {
                                        timeInfo.append(times.get(i));
                                        if (i < times.size() - 1) {
                                            timeInfo.append(", ");
                                        }
                                    }
                                }
                            }
                            c.sendMessage(Chat.colorize(timeInfo.toString()));
                            
                            // Hiển thị thông tin ngày
                            c.sendMessage(Chat.colorize("    &eNgày: &f" + days));
                        }
                    } else {
                        c.sendMessage(Chat.colorize("&cKhông có sự kiện nào được cấu hình."));
                    }
                }
            } else {
                c.sendMessage(Chat.colorize(File.getEvents().getString("commands.messages.no_permission")));
            }
        } else if (args[0].equalsIgnoreCase("toggle")) {
            if (c instanceof Player) {
                Player p = (Player) c;
                if (p.hasPermission("storage.toggle")) {
                    MineManager.toggle.replace(p, !MineManager.toggle.get(p));
                    p.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.status.toggle"))
                            .replace("#status#", ItemManager.getStatus(p))));
                }
            }
        } else if (args[0].equalsIgnoreCase("stats")) {
            if (c instanceof Player) {
                Player p = (Player) c;
                if (p.hasPermission("storage.stats")) {
                    try {
                        ((Player) c).openInventory(new StatsGUI((Player) c).getInventory());
                    } catch (Exception e) {
                        p.sendMessage(Chat.colorizewp("&e===== &fThống kê của bạn &e====="));
                        com.hongminh54.storage.Utils.StatsManager.getStatsInfo(p).forEach(s -> p.sendMessage(Chat.colorizewp(s)));
                    }
                } else {
                    p.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("admin.no_permission"))));
                }
            } else {
                c.sendMessage(Chat.colorize("&cLệnh này chỉ có thể được sử dụng bởi người chơi."));
            }
        } else if (args[0].equalsIgnoreCase("leaderboard")) {
            if (c instanceof Player) {
                Player p = (Player) c;
                if (p.hasPermission("storage.leaderboard")) {
                    try {
                        // Kiểm tra thời gian giữa các lần sử dụng bảng xếp hạng
                        // để tránh quá tải khi nhiều người dùng cùng lúc
                        String playerKey = "leaderboard_cooldown_" + p.getName();
                        Long lastUse = playerCooldowns.get(playerKey);
                        long currentTime = System.currentTimeMillis();
                        
                        if (lastUse != null && (currentTime - lastUse) < 2000) { // 2 giây cooldown
                            p.sendMessage(Chat.colorizewp("&eVui lòng đợi giây lát trước khi mở lại bảng xếp hạng."));
                            return;
                        }
                        
                        // Cập nhật thời gian sử dụng lệnh
                        playerCooldowns.put(playerKey, currentTime);
                        
                        // Xử lý các tham số đặc biệt
                        if (args.length > 1) {
                            // Chế độ làm mới bảng xếp hạng bắt buộc
                            if (args[1].equalsIgnoreCase("refresh") && p.hasPermission("storage.leaderboard.refresh")) {
                                p.sendMessage(Chat.colorizewp("&e&lĐang làm mới bảng xếp hạng..."));
                                // Xóa cache và cập nhật lại toàn bộ bảng xếp hạng
                                LeaderboardManager.clearCache();
                                LeaderboardManager.updateAllLeaderboards();
                                p.sendMessage(Chat.colorizewp("&a&lĐã làm mới bảng xếp hạng thành công!"));
                                
                                // Sau đó mở bảng xếp hạng với dữ liệu mới
                                LeaderboardGUI gui = new LeaderboardGUI(p);
                                p.openInventory(gui.getInventory());
                                return;
                            }
                            
                            // Chế độ debug cho admin
                            if (args[1].equalsIgnoreCase("debug") && p.hasPermission("storage.admin")) {
                            // Kiểm tra các loại bảng xếp hạng
                            p.sendMessage(Chat.colorizewp("&e&lDebug bảng xếp hạng - Xem console để biết chi tiết"));
                            LeaderboardManager.debugLeaderboard(LeaderboardManager.TYPE_MINED);
                            LeaderboardManager.debugLeaderboard(LeaderboardManager.TYPE_DEPOSITED);
                            LeaderboardManager.debugLeaderboard(LeaderboardManager.TYPE_WITHDRAWN);
                            LeaderboardManager.debugLeaderboard(LeaderboardManager.TYPE_SOLD);
                            return;
                        }
                        
                            // Chọn loại bảng xếp hạng cụ thể
                            if (args[1].equalsIgnoreCase("mined") || 
                                args[1].equalsIgnoreCase("deposited") || 
                                args[1].equalsIgnoreCase("withdrawn") || 
                                args[1].equalsIgnoreCase("sold")) {
                                
                                String type;
                                switch (args[1].toLowerCase()) {
                                    case "mined":
                                        type = LeaderboardManager.TYPE_MINED;
                                        break;
                                    case "deposited":
                                        type = LeaderboardManager.TYPE_DEPOSITED;
                                        break;
                                    case "withdrawn":
                                        type = LeaderboardManager.TYPE_WITHDRAWN;
                                        break;
                                    case "sold":
                                        type = LeaderboardManager.TYPE_SOLD;
                                        break;
                                    default:
                                        type = LeaderboardManager.TYPE_MINED;
                                }
                                
                                // Kiểm tra bảng xếp hạng chỉ định có dữ liệu
                                if (LeaderboardManager.getCachedLeaderboard(type, 1).isEmpty()) {
                                    p.sendMessage(Chat.colorizewp("&eBảng xếp hạng trống hoặc chưa được tạo. Đang tạo mới..."));
                                    LeaderboardManager.updateLeaderboard(type);
                                }
                                
                                LeaderboardGUI gui = new LeaderboardGUI(p, type);
                                p.openInventory(gui.getInventory());
                                return;
                            }
                        }
                        
                        // Kiểm tra xem có cần làm mới các bảng xếp hạng hay không
                        boolean needsUpdate = false;
                        
                        // Kiểm tra và cập nhật tất cả các loại bảng xếp hạng nếu trống
                        if (LeaderboardManager.getCachedLeaderboard(LeaderboardManager.TYPE_MINED, 1).isEmpty()) {
                            Storage.getStorage().getLogger().info("Bảng xếp hạng TYPE_MINED trống, đang cập nhật...");
                            LeaderboardManager.updateLeaderboard(LeaderboardManager.TYPE_MINED);
                            needsUpdate = true;
                        }
                        
                        if (LeaderboardManager.getCachedLeaderboard(LeaderboardManager.TYPE_DEPOSITED, 1).isEmpty()) {
                            Storage.getStorage().getLogger().info("Bảng xếp hạng TYPE_DEPOSITED trống, đang cập nhật...");
                            LeaderboardManager.updateLeaderboard(LeaderboardManager.TYPE_DEPOSITED);
                            needsUpdate = true;
                        }
                        
                        if (LeaderboardManager.getCachedLeaderboard(LeaderboardManager.TYPE_WITHDRAWN, 1).isEmpty()) {
                            Storage.getStorage().getLogger().info("Bảng xếp hạng TYPE_WITHDRAWN trống, đang cập nhật...");
                            LeaderboardManager.updateLeaderboard(LeaderboardManager.TYPE_WITHDRAWN);
                            needsUpdate = true;
                        }
                        
                        if (LeaderboardManager.getCachedLeaderboard(LeaderboardManager.TYPE_SOLD, 1).isEmpty()) {
                            Storage.getStorage().getLogger().info("Bảng xếp hạng TYPE_SOLD trống, đang cập nhật...");
                            LeaderboardManager.updateLeaderboard(LeaderboardManager.TYPE_SOLD);
                            needsUpdate = true;
                        }
                        
                        if (needsUpdate) {
                            p.sendMessage(Chat.colorizewp("&eDữ liệu bảng xếp hạng đã được cập nhật!"));
                        }
                        
                        // Mở GUI bảng xếp hạng với loại mặc định
                        LeaderboardGUI gui = new LeaderboardGUI(p);
                        p.openInventory(gui.getInventory());
                    } catch (Exception e) {
                        Storage.getStorage().getLogger().severe("Lỗi khi mở bảng xếp hạng: " + e.getMessage());
                        e.printStackTrace();
                        p.sendMessage(Chat.colorizewp("&cKhông thể mở bảng xếp hạng. Lỗi: " + e.getMessage()));
                        p.sendMessage(Chat.colorizewp("&eBạn có thể thử: &f/kho leaderboard refresh &eđể khắc phục lỗi"));
                    }
                } else {
                    p.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("admin.no_permission"))));
                }
            } else {
                c.sendMessage(Chat.colorize("&cLệnh này chỉ có thể được sử dụng bởi người chơi."));
            }
        } else if (args[0].equalsIgnoreCase("resetleaderboard")) {
            if (c.hasPermission("storage.admin.resetleaderboard")) {
                try {
                    // Hiển thị thông báo bắt đầu
                    c.sendMessage(Chat.colorize("&a&lĐang reset bảng xếp hạng, vui lòng đợi..."));
                    
                    // Thực hiện reset
                    int affectedRows = LeaderboardManager.resetLeaderboard();
                    
                    // Reset cache của các người chơi online để đảm bảo dữ liệu mới
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        StatsManager.removeFromCache(onlinePlayer.getName());
                        StatsManager.loadPlayerStats(onlinePlayer, true);
                    }
                    
                    // Thông báo thành công
                    String message = Chat.colorize("&a&lĐã reset bảng xếp hạng thành công! &7(" + affectedRows + " người chơi)");
                    c.sendMessage(message);
                    
                    // Thông báo broadcast
                    String broadcastMessage = Chat.colorize("&c&l[Kho Khoáng Sản] &eBảng xếp hạng đã được reset bởi &f" + c.getName());
                    Bukkit.broadcastMessage(broadcastMessage);
                    
                } catch (Exception e) {
                    String errorMessage = Chat.colorize("&cKhông thể reset bảng xếp hạng. Vui lòng kiểm tra console để biết thêm chi tiết.");
                    c.sendMessage(errorMessage);
                    Storage.getStorage().getLogger().severe("Lỗi khi reset bảng xếp hạng: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                c.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("admin.no_permission"))));
            }
        } else if (args[0].equalsIgnoreCase("resetstats")) {
            if (c.hasPermission("storage.admin.resetstats")) {
                if (args.length < 2) {
                    c.sendMessage(Chat.colorize("&cCách sử dụng: /storage resetstats <tên người chơi>"));
                    return;
                }
                String playerName = args[1];
                
                // Kiểm tra tên người chơi hợp lệ
                if (playerName.length() < 3 || playerName.length() > 16) {
                    c.sendMessage(Chat.colorize("&cTên người chơi không hợp lệ!"));
                    return;
                }
                
                try {
                    boolean success = StatsManager.resetPlayerStats(playerName);
                    if (success) {
                        String message = Chat.colorize("&aĐã reset thống kê của người chơi &f" + playerName + "&a thành công!");
                        c.sendMessage(message);
                        
                        // Thông báo cho người chơi bị reset nếu họ đang online
                        Player targetPlayer = Bukkit.getPlayer(playerName);
                        if (targetPlayer != null) {
                            String playerMessage = Chat.colorize("&c&l[Kho Khoáng Sản] &eThống kê của bạn đã được reset bởi &f" + c.getName());
                            targetPlayer.sendMessage(playerMessage);
                            
                            // Tải lại dữ liệu cho người chơi
                            StatsManager.loadPlayerStats(targetPlayer);
                        }
                        
                        Storage.getStorage().getLogger().info("Thống kê của người chơi " + playerName + " đã được reset bởi " + c.getName());
                    } else {
                        c.sendMessage(Chat.colorize("&cKhông thể reset thống kê của người chơi &f" + playerName));
                    }
                } catch (Exception e) {
                    String errorMessage = Chat.colorize("&cKhông thể reset thống kê. Vui lòng kiểm tra console để biết thêm chi tiết.");
                    c.sendMessage(errorMessage);
                    Storage.getStorage().getLogger().severe("Lỗi khi reset thống kê của người chơi " + playerName + ": " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                c.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("admin.no_permission"))));
            }
        } else if (args[0].equalsIgnoreCase("update")) {
            if (c instanceof Player) {
                Player p = (Player) c;
                if (p.hasPermission("storage.admin")) {
                    List<UpdateChecker> checkers = Storage.getUpdateCheckers();
                    UpdateChecker updateChecker = null;
                    
                    if (checkers != null && !checkers.isEmpty()) {
                        for (UpdateChecker checker : checkers) {
                            if (checker.hasPendingDownload(p.getUniqueId())) {
                                updateChecker = checker;
                                break;
                            }
                        }
                    }
                    
                    if (updateChecker != null) {
                        updateChecker.downloadUpdate(p);
                    } else {
                        p.sendMessage(Chat.colorize("&cKhông có cập nhật nào đang chờ tải xuống."));
                    }
                } else {
                    p.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("admin.no_permission"))));
                }
            } else {
                c.sendMessage(Chat.colorize("&cLệnh này chỉ có thể được sử dụng bởi người chơi."));
            }
        } else if (args[0].equalsIgnoreCase("chuyenore") || args[0].equalsIgnoreCase("transfer")) {
            if (c instanceof Player) {
                Player player = (Player) c;
                if (!player.hasPermission("storage.transfer")) {
                    player.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("admin.no_permission"))));
                    String failSoundConfig = File.getConfig().getString("effects.permission_fail.sound", "ENTITY_VILLAGER_NO:1.0:1.0");
                    SoundManager.playSoundFromConfig(player, failSoundConfig);
                    return;
                }

                if (args.length == 1) {
                    // Nếu chỉ có lệnh /kho chuyenore, mở giao diện tìm kiếm người chơi
                    try {
                        player.openInventory(new PlayerSearchGUI(player).getInventory());
                        // Phát âm thanh khi mở giao diện tìm kiếm
                        String searchSoundConfig = File.getConfig().getString("effects.search_open.sound", "BLOCK_NOTE_BLOCK_PLING:0.5:1.2");
                        SoundManager.playSoundFromConfig(player, searchSoundConfig);
                    } catch (Exception e) {
                        player.sendMessage(Chat.colorize("&8[&4&l✕&8] &cLỗi khi mở giao diện tìm kiếm: " + e.getMessage()));
                        Storage.getStorage().getLogger().warning("Lỗi khi mở giao diện tìm kiếm: " + e.getMessage());
                        e.printStackTrace();
                    }
                    return;
                }

                // Tìm người chơi nhận tài nguyên
                Player receiver = Bukkit.getPlayer(args[1]);
                if (receiver == null || !receiver.isOnline()) {
                    player.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.action.transfer.player_not_online")).replace("#player#", args[1])));
                    // Phát âm thanh thất bại
                    String failSoundConfig = File.getConfig().getString("effects.transfer_fail.sound", "ENTITY_VILLAGER_NO:1.0:1.0");
                    SoundManager.playSoundFromConfig(player, failSoundConfig);
                    return;
                }

                // Người chơi không thể tự chuyển tài nguyên cho chính mình
                if (receiver.equals(player)) {
                    player.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.action.transfer.cannot_transfer_to_self"))));
                    // Phát âm thanh thất bại
                    String failSoundConfig = File.getConfig().getString("effects.transfer_fail.sound", "ENTITY_VILLAGER_NO:1.0:1.0");
                    SoundManager.playSoundFromConfig(player, failSoundConfig);
                    return;
                }

                if (args.length == 2) {
                    // Mở GUI kho cá nhân để chọn tài nguyên
                    try {
                        player.openInventory(new PersonalStorage(player).getInventory());
                        player.sendMessage(Chat.colorize("&8[&a&l✓&8] &aVui lòng chọn loại tài nguyên muốn chuyển cho &e" + receiver.getName()));
                        // Phát âm thanh khi mở kho cá nhân
                        String openSoundConfig = File.getConfig().getString("effects.storage_open.sound", "BLOCK_CHEST_OPEN:0.5:1.0");
                        SoundManager.playSoundFromConfig(player, openSoundConfig);
                    } catch (Exception e) {
                        player.sendMessage(Chat.colorize("&8[&4&l✕&8] &cLỗi khi mở kho cá nhân: " + e.getMessage()));
                        Storage.getStorage().getLogger().warning("Lỗi khi mở kho cá nhân: " + e.getMessage());
                        e.printStackTrace();
                    }
                    return;
                } else if (args.length >= 3) {
                    // Kiểm tra tài nguyên hoặc loại giao diện
                    String option = args[2].toLowerCase();
                    
                    if (option.equals("multiple") || option.equals("multi") || option.equals("nhiều") || option.equals("nhieutainguyen")) {
                        // Mở giao diện chuyển nhiều tài nguyên
                        try {
                            player.openInventory(new MultiTransferGUI(player, receiver).getInventory());
                            // Phát âm thanh khi mở giao diện chuyển nhiều tài nguyên
                            String clickSoundConfig = File.getConfig().getString("effects.gui_click.sound", "UI_BUTTON_CLICK:0.5:1.0");
                            SoundManager.playSoundFromConfig(player, clickSoundConfig);
                        } catch (Exception e) {
                            player.sendMessage(Chat.colorize("&8[&4&l✕&8] &cLỗi khi mở giao diện chuyển nhiều tài nguyên: " + e.getMessage()));
                            Storage.getStorage().getLogger().warning("Lỗi khi mở giao diện chuyển nhiều tài nguyên: " + e.getMessage());
                            e.printStackTrace();
                        }
                    } else if (MineManager.getPluginBlocks().contains(args[2])) {
                        // Mở GUI chuyển tài nguyên cụ thể
                        try {
                            player.openInventory(new TransferGUI(player, receiver, args[2]).getInventory());
                            // Phát âm thanh khi mở giao diện chuyển tài nguyên
                            String clickSoundConfig = File.getConfig().getString("effects.gui_click.sound", "UI_BUTTON_CLICK:0.5:1.0");
                            SoundManager.playSoundFromConfig(player, clickSoundConfig);
                        } catch (Exception e) {
                            player.sendMessage(Chat.colorize("&8[&4&l✕&8] &cLỗi khi mở giao diện chuyển tài nguyên: " + e.getMessage()));
                            Storage.getStorage().getLogger().warning("Lỗi khi mở giao diện chuyển tài nguyên: " + e.getMessage());
                            e.printStackTrace();
                        }
                    } else {
                        // Tài nguyên không hợp lệ
                        player.sendMessage(Chat.colorize("&8[&4&l✕&8] &cLoại tài nguyên không tồn tại: &f" + args[2] + "\n&8[&e&l!&8] &eSử dụng &f/kho chuyenore " + receiver.getName() + " multi &eđể chuyển nhiều loại"));
                        // Phát âm thanh thất bại
                        String failSoundConfig = File.getConfig().getString("effects.transfer_fail.sound", "ENTITY_VILLAGER_NO:1.0:1.0");
                        SoundManager.playSoundFromConfig(player, failSoundConfig);
                    }
                    return;
                }
            } else {
                c.sendMessage(Chat.colorize("&8[&4&l✕&8] &cLệnh này chỉ có thể được sử dụng bởi người chơi!"));
            }
        } else if (args[0].equalsIgnoreCase("log") || args[0].equalsIgnoreCase("lichsu") || args[0].equalsIgnoreCase("history")) {
            if (c instanceof Player) {
                Player p = (Player) c;
                if (c.hasPermission("storage.transfer")) {
                    // Kiểm tra nếu người dùng yêu cầu trợ giúp
                    for (int i = 1; i < args.length; i++) {
                        if (args[i].equalsIgnoreCase("-help") || args[i].equalsIgnoreCase("-h") || args[i].equalsIgnoreCase("-?")) {
                            displayLogHelp(p);
                            return;
                        }
                    }
                    
                    // Các biến tìm kiếm
                    String targetPlayer = p.getName();
                    int page = 0;
                    String materialFilter = null;
                    boolean showStats = false;
                    long startTime = 0;
                    long endTime = System.currentTimeMillis();
                    String sortOrder = "desc"; // mặc định là từ mới đến cũ
                    
                    // Xử lý các tham số
                    for (int i = 1; i < args.length; i++) {
                        String arg = args[i].toLowerCase();
                        
                        // Xử lý số trang
                        if (arg.matches("\\d+") && !arg.startsWith("-")) {
                            try {
                                page = Integer.parseInt(args[i]) - 1; // Chuyển sang 0-based
                                if (page < 0) page = 0;
                                continue;
                            } catch (NumberFormatException e) {
                                // Bỏ qua lỗi
                            }
                        }
                        
                        // Xử lý các tham số với dấu -
                        if (arg.startsWith("-")) {
                            // Lọc theo loại tài nguyên
                            if (arg.startsWith("-m:") || arg.startsWith("-material:")) {
                                String material = arg.split(":", 2)[1];
                                materialFilter = material;
                                continue;
                            }
                            
                            // Hiển thị thống kê
                            if (arg.equals("-stats") || arg.equals("-thongke")) {
                                showStats = true;
                                continue;
                            }
                            
                            // Lọc theo thời gian bắt đầu
                            if (arg.startsWith("-from:") || arg.startsWith("-tu:")) {
                                try {
                                    int days = Integer.parseInt(arg.split(":", 2)[1]);
                                    startTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L);
                                    continue;
                                } catch (Exception e) {
                                    // Bỏ qua nếu định dạng không đúng
                                }
                            }
                            
                            // Lọc theo thời gian kết thúc
                            if (arg.startsWith("-to:") || arg.startsWith("-den:")) {
                                try {
                                    int days = Integer.parseInt(arg.split(":", 2)[1]);
                                    endTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L);
                                    continue;
                                } catch (Exception e) {
                                    // Bỏ qua nếu định dạng không đúng
                                }
                            }
                            
                            // Sắp xếp theo thứ tự cũ đến mới
                            if (arg.equals("-asc") || arg.equals("-cudenmoiden")) {
                                sortOrder = "asc";
                                continue;
                            }
                            
                            // Sắp xếp theo thứ tự mới đến cũ (mặc định)
                            if (arg.equals("-desc") || arg.equals("-moidencu")) {
                                sortOrder = "desc";
                                continue;
                            }
                            
                            continue;
                        }
                        
                        // Nếu không phải tham số đặc biệt, coi là tên người chơi
                        if (!arg.matches("\\d+") && !arg.startsWith("-")) {
                            targetPlayer = args[i];
                        }
                    }
                    
                    // Kiểm tra quyền nếu xem người khác
                    if (!targetPlayer.equalsIgnoreCase(p.getName()) && !c.hasPermission("storage.transfer.others")) {
                        p.sendMessage(Chat.colorize(File.getMessage().getString("user.action.transfer.history_no_permission", "&8[&c&l✕&8] &cBạn không có quyền xem lịch sử chuyển kho của người khác.")));
                        return;
                    }
                    
                    // Phát âm thanh xác nhận khi xem lịch sử
                    String viewSoundConfig = File.getConfig().getString("effects.history_view.sound", "BLOCK_NOTE_BLOCK_PLING:0.5:1.2");
                    SoundManager.playSoundFromConfig(p, viewSoundConfig);
                    
                    // Hiển thị thông tin tìm kiếm
                    StringBuilder searchInfoBuilder = new StringBuilder();
                    searchInfoBuilder.append(Chat.colorize("&e&l>> &fTìm kiếm lịch sử:"));
                    
                    if (!targetPlayer.equalsIgnoreCase(p.getName())) {
                        searchInfoBuilder.append(Chat.colorize(" &7Người chơi: &f" + targetPlayer));
                    }
                    
                    if (materialFilter != null) {
                        String displayName = File.getConfig().getString("items." + materialFilter, materialFilter);
                        searchInfoBuilder.append(Chat.colorize(" &7Vật phẩm: &f" + displayName));
                    }
                    
                    if (startTime > 0) {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
                        searchInfoBuilder.append(Chat.colorize(" &7Từ: &f" + dateFormat.format(new java.util.Date(startTime))));
                    }
                    
                    if (endTime < System.currentTimeMillis()) {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
                        searchInfoBuilder.append(Chat.colorize(" &7Đến: &f" + dateFormat.format(new java.util.Date(endTime))));
                    }
                    
                    // Hiển thị thông tin tìm kiếm nếu có bộ lọc
                    if (materialFilter != null || startTime > 0 || endTime < System.currentTimeMillis()) {
                        p.sendMessage(searchInfoBuilder.toString());
                    }
                    
                    // Lấy danh sách lịch sử với bộ lọc
                    List<TransferHistory> historyList = getFilteredTransferHistory(
                        targetPlayer, page, 
                        File.getConfig().getInt("settings.history_items_per_page_chat", 20),
                        materialFilter, startTime, endTime, sortOrder
                    );
                    
                    // Hiển thị thống kê nếu được yêu cầu
                    if (showStats) {
                        displayTransferStats(p, targetPlayer, materialFilter, startTime, endTime);
                    }
                    
                    // Tính tổng số mục phù hợp với bộ lọc (để phân trang)
                    int totalFilteredItems = countFilteredTransferHistory(
                        targetPlayer, materialFilter, startTime, endTime
                    );
                    
                    int limit = File.getConfig().getInt("settings.history_items_per_page_chat", 20);
                    int totalPages = (int) Math.ceil((double) totalFilteredItems / limit);
                    
                    if (page >= totalPages) {
                        page = Math.max(0, totalPages - 1);
                    }
                    
                    // Hiển thị tiêu đề
                    String title;
                    if (targetPlayer.equalsIgnoreCase(p.getName())) {
                        title = File.getMessage().getString("user.action.transfer.history_title", "&8≫ &e&lLịch Sử &f#count# &e&lGiao Dịch Gần Đây")
                                .replace("#count#", String.valueOf(Math.min(limit, historyList.size())));
                    } else {
                        title = File.getMessage().getString("user.action.transfer.history_player_title", "&8≫ &e&lLịch Sử Giao Dịch của &f#player#")
                                .replace("#player#", targetPlayer);
                    }
                    
                    p.sendMessage(Chat.colorize(title));
                    
                    // Hiển thị thông tin trang
                    if (totalPages > 1) {
                        String pageInfo = File.getMessage().getString("user.action.transfer.page_info", "&7Trang &f#current#&7/&f#total#")
                                .replace("#current#", String.valueOf(page + 1))
                                .replace("#total#", String.valueOf(totalPages));
                        p.sendMessage(Chat.colorize(pageInfo));
                    }
                    
                    // Nếu không có lịch sử
                    if (historyList.isEmpty()) {
                        String noHistoryMsg;
                        if (targetPlayer.equalsIgnoreCase(p.getName())) {
                            if (materialFilter != null) {
                                noHistoryMsg = "&eKhông có lịch sử giao dịch nào cho vật phẩm này.";
                            } else {
                            noHistoryMsg = File.getMessage().getString("user.action.transfer.no_history", "&eKhông có lịch sử giao dịch nào.");
                            }
                        } else {
                            if (materialFilter != null) {
                                noHistoryMsg = "&eNgười chơi #player# chưa có giao dịch nào với vật phẩm này.".replace("#player#", targetPlayer);
                        } else {
                            noHistoryMsg = File.getMessage().getString("user.action.transfer.player_no_history", "&eNgười chơi #player# chưa có giao dịch chuyển tài nguyên nào.")
                                    .replace("#player#", targetPlayer);
                            }
                        }
                        p.sendMessage(Chat.colorize(noHistoryMsg));
                        p.sendMessage(Chat.colorize("&aTip: &fSử dụng &e/kho log -help &fđể xem các tùy chọn tìm kiếm"));
                        return;
                    }
                    
                    // Hiển thị danh sách lịch sử
                    SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                    
                    for (TransferHistory history : historyList) {
                        String materialName = File.getConfig().getString("items." + history.getMaterial(), 
                                history.getMaterial().contains(";") ? history.getMaterial().split(";")[0] : history.getMaterial());
                        
                        // Định dạng thời gian
                        String time = dateFormat.format(history.getDate());
                        
                        // Tạo tin nhắn phù hợp
                        String message;
                        if (history.getSenderName().equalsIgnoreCase(targetPlayer)) {
                            // Người chơi là người gửi
                            message = File.getMessage().getString("user.action.transfer.transfer_to", "&8[&e#time#&8] &f#player# đã chuyển &a#amount# #material# &fcho &e#receiver#")
                                    .replace("#time#", time)
                                    .replace("#player#", targetPlayer.equals(p.getName()) ? "Bạn" : targetPlayer)
                                    .replace("#amount#", String.valueOf(history.getAmount()))
                                    .replace("#material#", materialName)
                                    .replace("#receiver#", history.getReceiverName());
                        } else {
                            // Người chơi là người nhận
                            message = File.getMessage().getString("user.action.transfer.transfer_from", "&8[&e#time#&8] &f#player# đã nhận &a#amount# #material# &ftừ &e#sender#")
                                    .replace("#time#", time)
                                    .replace("#player#", targetPlayer.equals(p.getName()) ? "Bạn" : targetPlayer)
                                    .replace("#amount#", String.valueOf(history.getAmount()))
                                    .replace("#material#", materialName)
                                    .replace("#sender#", history.getSenderName());
                        }
                        
                        p.sendMessage(Chat.colorize(message));
                    }
                    
                    // Hiển thị trợ giúp về cách sử dụng tìm kiếm nâng cao
                    if (materialFilter == null && startTime == 0 && endTime == System.currentTimeMillis()) {
                        p.sendMessage(Chat.colorize("&aTip: &fSử dụng &e/kho log -help &fđể xem các tùy chọn tìm kiếm"));
                    }
                    
                    // Hiển thị nút điều hướng (chỉ khi có nhiều trang)
                    if (totalPages > 1) {
                        net.md_5.bungee.api.chat.TextComponent navComponent = new net.md_5.bungee.api.chat.TextComponent("");
                        
                        // Nút trang trước
                        if (page > 0) {
                            net.md_5.bungee.api.chat.TextComponent prevButton = new net.md_5.bungee.api.chat.TextComponent(Chat.colorize("&e« "));
                            prevButton.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                                new net.md_5.bungee.api.chat.ComponentBuilder(Chat.colorize("&eNhấp để xem trang trước")).create()
                            ));
                            
                            // Tạo lệnh với các tham số tìm kiếm
                            String command = buildLogCommand(targetPlayer, page, materialFilter, startTime, endTime, sortOrder, showStats);
                            prevButton.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                                command
                            ));
                            navComponent.addExtra(prevButton);
                        } else {
                            navComponent.addExtra(Chat.colorize("&7« "));
                        }
                        
                        // Các nút trang
                        int startPage = Math.max(0, page - 2);
                        int endPage = Math.min(totalPages - 1, page + 2);
                        
                        for (int i = startPage; i <= endPage; i++) {
                            net.md_5.bungee.api.chat.TextComponent pageButton;
                            
                            if (i == page) {
                                pageButton = new net.md_5.bungee.api.chat.TextComponent(Chat.colorize("&a[" + (i + 1) + "] "));
                            } else {
                                pageButton = new net.md_5.bungee.api.chat.TextComponent(Chat.colorize("&e[" + (i + 1) + "] "));
                                pageButton.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                                    new net.md_5.bungee.api.chat.ComponentBuilder(Chat.colorize("&eNhấp để đến trang " + (i + 1))).create()
                                ));
                                
                                // Tạo lệnh với các tham số tìm kiếm
                                String command = buildLogCommand(targetPlayer, i, materialFilter, startTime, endTime, sortOrder, showStats);
                                pageButton.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                                    command
                                ));
                            }
                            
                            navComponent.addExtra(pageButton);
                        }
                        
                        // Nút trang sau
                        if (page < totalPages - 1) {
                            net.md_5.bungee.api.chat.TextComponent nextButton = new net.md_5.bungee.api.chat.TextComponent(Chat.colorize("&e»"));
                            nextButton.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                                new net.md_5.bungee.api.chat.ComponentBuilder(Chat.colorize("&eNhấp để xem trang tiếp theo")).create()
                            ));
                            
                            // Tạo lệnh với các tham số tìm kiếm
                            String command = buildLogCommand(targetPlayer, page + 2, materialFilter, startTime, endTime, sortOrder, showStats);
                            nextButton.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                                command
                            ));
                            navComponent.addExtra(nextButton);
                        } else {
                            navComponent.addExtra(Chat.colorize("&7»"));
                        }
                        
                        p.spigot().sendMessage(navComponent);
                    }
                } else {
                    c.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.no_permission"))));
                }
            } else {
                c.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("admin.only_player"))));
            }
        } else if (args[0].equalsIgnoreCase("logall") || args[0].equalsIgnoreCase("lichsuall") || args[0].equalsIgnoreCase("historyall")) {
            if (c instanceof Player) {
                Player p = (Player) c;
                if (c.hasPermission("storage.transfer")) {
                    String targetPlayer = p.getName();
                    
                    // Kiểm tra nếu người chơi muốn xem lịch sử của người khác
                    if (args.length >= 2) {
                        targetPlayer = args[1];
                        
                        // Kiểm tra quyền nếu xem người khác
                        if (!targetPlayer.equalsIgnoreCase(p.getName()) && !c.hasPermission("storage.transfer.others")) {
                            p.sendMessage(Chat.colorize(File.getMessage().getString("user.action.transfer.history_no_permission", 
                                    "&8[&c&l✕&8] &cBạn không có quyền xem lịch sử chuyển kho của người khác.")));
                            return;
                        }
                    }
                    
                    // Thiết lập chế độ xem đầy đủ log
                    setViewFullLog(p.getUniqueId().toString(), targetPlayer);
                    
                    // Hiển thị thông báo
                    String enabledMsg = File.getMessage().getString("user.action.transfer.log_all_enabled", 
                            "&a&l✓ &aĐã hiển thị toàn bộ lịch sử giao dịch của &f#player#&a.")
                            .replace("#player#", targetPlayer);
                    p.sendMessage(Chat.colorize(enabledMsg));
                    
                    // Tự động hiển thị log
                    String[] logArgs = new String[]{"log"};
                    if (!targetPlayer.equals(p.getName())) {
                        logArgs = new String[]{"log", targetPlayer};
                    }
                    execute(p, logArgs);
                } else {
                    c.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("user.no_permission"))));
                }
            } else {
                c.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("admin.only_player"))));
            }
        } else if (args[0].equalsIgnoreCase("search") || args[0].equalsIgnoreCase("timkiem") || args[0].equalsIgnoreCase("find")) {
            if (c instanceof Player) {
                Player player = (Player) c;
                if (!player.hasPermission("storage.transfer")) {
                    player.sendMessage(Chat.colorize("&cBạn không có quyền sử dụng lệnh này!"));
                    // Phát âm thanh thất bại
                    String failSoundConfig = File.getConfig().getString("effects.permission_fail.sound", "ENTITY_VILLAGER_NO:1.0:1.0");
                    SoundManager.playSoundFromConfig(player, failSoundConfig);
                    return;
                }

                try {
                    player.openInventory(new PlayerSearchGUI(player).getInventory());
                    // Phát âm thanh khi mở giao diện tìm kiếm
                    String searchSoundConfig = File.getConfig().getString("effects.search_open.sound", "BLOCK_NOTE_BLOCK_PLING:0.5:1.2");
                    SoundManager.playSoundFromConfig(player, searchSoundConfig);
                } catch (Exception e) {
                    player.sendMessage(Chat.colorize("&8[&4&l✕&8] &cLỗi khi mở giao diện tìm kiếm: " + e.getMessage()));
                    Storage.getStorage().getLogger().warning("Lỗi khi mở giao diện tìm kiếm: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                c.sendMessage(Chat.colorize("&8[&4&l✕&8] &cLệnh này chỉ có thể được sử dụng bởi người chơi!"));
            }
        }
        if (c.hasPermission("storage.admin")
                || c.hasPermission("storage.admin.reload")) {
            if (args.length == 1) {
                if (args[0].equalsIgnoreCase("reload")) {
                    File.reloadFiles();
                    MineManager.loadBlocks();
                    for (Player p : Storage.getStorage().getServer().getOnlinePlayers()) {
                        MineManager.convertOfflineData(p);
                        MineManager.loadPlayerData(p);
                    }
                    c.sendMessage(Chat.colorize(File.getMessage().getString("admin.reload")));
                }
            }
        }
        if (c.hasPermission("storage.admin")
                || c.hasPermission("storage.admin.max")) {
            if (args.length == 3) {
                if (args[0].equalsIgnoreCase("max")) {
                    Player p = Bukkit.getPlayer(args[1]);
                    if (p != null) {
                        int amount = Number.getInteger(args[2]);
                        if (amount > 0) {
                            MineManager.playermaxdata.put(p, amount);
                            c.sendMessage(Chat.colorize(Objects.requireNonNull(File.getMessage().getString("admin.set_max_storage")).replace("#player#", p.getName()).replace("#amount#", String.valueOf(amount))));
                        }
                    }
                }
            }
        }
        if (c.hasPermission("storage.admin")
                || c.hasPermission("storage.admin.add") || c.hasPermission("storage.admin.remove") || c.hasPermission("storage.admin.set")) {
            if (args.length == 4) {
                if (MineManager.getPluginBlocks().contains(args[1])) {
                    Player p = Bukkit.getPlayer(args[2]);
                        int number = Number.getInteger(args[3]);
                        if (number >= 0) {
                        // Xử lý lệnh add
                            if (args[0].equalsIgnoreCase("add")) {
                            if (c.hasPermission("storage.admin") || c.hasPermission("storage.admin.add")) {
                                handleAddCommand(c, args, p, number);
                            }
                        }
                        // Xử lý lệnh remove
                            if (args[0].equalsIgnoreCase("remove")) {
                                if (c.hasPermission("storage.admin") || c.hasPermission("storage.admin.remove")) {
                                if (p != null) {
                                    if (MineManager.removeBlockAmount(p, args[1], number)) {
                                        c.sendMessage(Chat.colorize(File.getMessage().getString("admin.remove_material_amount")).replace("#amount#", args[3]).replace("#material#", args[1]).replace("#player#", p.getName()));
                                        p.sendMessage(Chat.colorize(File.getMessage().getString("user.remove_material_amount")).replace("#amount#", args[3]).replace("#material#", args[1]).replace("#player#", c.getName()));
                                    }
                                } else {
                                    c.sendMessage(Chat.colorize("&cNgười chơi không online. Tính năng xóa khoáng sản cho người chơi offline hiện không được hỗ trợ."));
                                }
                            }
                        }
                        // Xử lý lệnh set
                            if (args[0].equalsIgnoreCase("set")) {
                                if (c.hasPermission("storage.admin") || c.hasPermission("storage.admin.set")) {
                                handleSetCommand(c, args, p, number);
                            }
                        }
                    }
                }
            }
        } else if (args[0].equalsIgnoreCase("cachestats")) {
            if (c.hasPermission("storage.admin") || c.hasPermission("storage.admin.cachestats")) {
                if (args.length == 1) {
                    // Hiển thị thống kê cache
                    List<String> stats = CacheCoordinator.getCacheStats();
                    c.sendMessage(Chat.colorize(File.getMessage().getString("prefix") + "&e=== Thống kê Cache ==="));
                    for (String line : stats) {
                        c.sendMessage(Chat.colorize(line));
                    }
                } else if (args.length == 2 && args[1].equalsIgnoreCase("sync")) {
                    // Đồng bộ hóa cache
                    c.sendMessage(Chat.colorize(File.getMessage().getString("prefix") + "&aBắt đầu đồng bộ hóa cache..."));
                    if (c instanceof Player) {
                        Player p = (Player) c;
                        Bukkit.getScheduler().runTaskAsynchronously(Storage.getStorage(), () -> {
                            long startTime = System.currentTimeMillis();
                            CacheCoordinator.synchronizeAllPlayerCache();
                            long duration = System.currentTimeMillis() - startTime;
                            p.sendMessage(Chat.colorize(File.getMessage().getString("prefix") + "&aĐã đồng bộ hóa cache trong &f" + duration + "ms"));
                        });
                    } else {
                        long startTime = System.currentTimeMillis();
                        CacheCoordinator.synchronizeAllPlayerCache();
                        long duration = System.currentTimeMillis() - startTime;
                        c.sendMessage(Chat.colorize(File.getMessage().getString("prefix") + "&aĐã đồng bộ hóa cache trong &f" + duration + "ms"));
                    }
                }
            } else {
                c.sendMessage(Chat.colorize(File.getMessage().getString("no-permission")));
            }
        }
    }

    @Override
    public List<String> TabComplete(@NotNull CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> commands = new ArrayList<>();
        List<String> adminPerms = Arrays.asList("storage.admin.add", "storage.admin.remove", "storage.admin.set", "storage.admin.reload", "storage.admin.max");
        
        if (args.length == 1) {
            commands.add("help");
            if (sender.hasPermission("storage.toggle")) {
                commands.add("toggle");
            }
            if (sender.hasPermission("storage.stats")) {
                commands.add("stats");
            }
            if (sender.hasPermission("storage.leaderboard")) {
                commands.add("leaderboard");
            }
            if (sender.hasPermission("storage.transfer")) {
                commands.add("chuyenore");
                commands.add("transfer");
                commands.add("log");
                commands.add("logall");
                commands.add("search");
                commands.add("timkiem");
                commands.add("find");
            }
            if (sender.hasPermission("storage.admin")) {
                commands.add("add");
                commands.add("remove");
                commands.add("set");
                commands.add("reload");
                commands.add("max");
                commands.add("update");
                commands.add("resetleaderboard");
                commands.add("resetstats");
                commands.add("cachestats");
            } else {
                if (sender.hasPermission("storage.admin.add")) commands.add("add");
                if (sender.hasPermission("storage.admin.remove")) commands.add("remove");
                if (sender.hasPermission("storage.admin.set")) commands.add("set");
                if (sender.hasPermission("storage.admin.reload")) commands.add("reload");
                if (sender.hasPermission("storage.admin.max")) commands.add("max");
                if (sender.hasPermission("storage.admin.resetleaderboard")) commands.add("resetleaderboard");
                if (sender.hasPermission("storage.admin.resetstats")) commands.add("resetstats");
                if (sender.hasPermission("storage.admin.cachestats")) commands.add("cachestats");
            }
            
            // Thêm lệnh event nếu có quyền
            if (sender.hasPermission("storage.event")) {
                commands.add("event");
            }
            
            StringUtil.copyPartialMatches(args[0], commands, completions);
        }
        
        if (args.length == 2) {
            // Tab completion cho lệnh cachestats
            if (args[0].equalsIgnoreCase("cachestats") && 
                (sender.hasPermission("storage.admin") || sender.hasPermission("storage.admin.cachestats"))) {
                commands.add("sync");
                StringUtil.copyPartialMatches(args[1], commands, completions);
            }
            
            // Tab completion cho lệnh chuyển tài nguyên
            if ((args[0].equalsIgnoreCase("chuyenore") || args[0].equalsIgnoreCase("transfer")) && 
                sender.hasPermission("storage.transfer")) {
                // Hiển thị danh sách người chơi online
                Bukkit.getServer().getOnlinePlayers().forEach(player -> {
                    // Không hiển thị tên người gửi trong danh sách
                    if (!player.getName().equals(sender.getName())) {
                        commands.add(player.getName());
                    }
                });
                StringUtil.copyPartialMatches(args[1], commands, completions);
            }
            
            // Thêm tab completion cho lệnh log/history/lichsu
            if ((args[0].equalsIgnoreCase("log") || args[0].equalsIgnoreCase("history") || args[0].equalsIgnoreCase("lichsu")) &&
                sender.hasPermission("storage.transfer")) {
                if (sender.hasPermission("storage.transfer.others")) {
                    // Sử dụng hàm getOnlinePlayerNames để lọc tên người chơi phù hợp
                    return getOnlinePlayerNames(args[1]);
                }
            }
            
            // Thêm tab completion cho lệnh leaderboard
            if (args[0].equalsIgnoreCase("leaderboard") && sender.hasPermission("storage.leaderboard")) {
                List<String> leaderboardOptions = new ArrayList<>();
                // Các loại bảng xếp hạng
                leaderboardOptions.add("mined");
                leaderboardOptions.add("deposited");
                leaderboardOptions.add("withdrawn");
                leaderboardOptions.add("sold");
                
                // Tùy chọn làm mới và debug
                if (sender.hasPermission("storage.leaderboard.refresh")) {
                    leaderboardOptions.add("refresh");
                }
                if (sender.hasPermission("storage.admin")) {
                    leaderboardOptions.add("debug");
                }
                
                StringUtil.copyPartialMatches(args[1], leaderboardOptions, completions);
            }
            
            // Thêm tab completion cho lệnh event
            if (args[0].equalsIgnoreCase("event") && sender.hasPermission("storage.event")) {
                StringUtil.copyPartialMatches(args[1], Arrays.asList("start", "stop", "info", "reload", "schedule"), completions);
            }
            
            // Tab completion cho lệnh resetstats
            if (sender.hasPermission("storage.admin")) {
                if (args[0].equalsIgnoreCase("resetstats")) {
                    // Chỉ hiển thị tab completion cho người chơi online
                    Bukkit.getServer().getOnlinePlayers().forEach(player -> commands.add(player.getName()));
                    StringUtil.copyPartialMatches(args[1], commands, completions);
                }
            }
            
            // Tab completion cho lệnh add, remove, set
            if (sender.hasPermission("storage.admin")
                    || sender.hasPermission("storage.admin.add") || sender.hasPermission("storage.admin.remove") || sender.hasPermission("storage.admin.set")) {
                if (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("set")) {
                    commands.addAll(MineManager.getPluginBlocks());
                        StringUtil.copyPartialMatches(args[1], commands, completions);
                }
            }
            
            // Tab completion cho lệnh max
            if (sender.hasPermission("storage.admin")
                    || sender.hasPermission("storage.admin.max")) {
                if (args[0].equalsIgnoreCase("max")) {
                    Bukkit.getServer().getOnlinePlayers().forEach(player -> commands.add(player.getName()));
                    StringUtil.copyPartialMatches(args[1], commands, completions);
                }
            }
        }
        
        if (args.length == 3) {
            // Tab completion cho lệnh chuyển tài nguyên
            if ((args[0].equalsIgnoreCase("chuyenore") || args[0].equalsIgnoreCase("transfer")) && 
                sender.hasPermission("storage.transfer")) {
                // Hiển thị danh sách tài nguyên
                commands.addAll(MineManager.getPluginBlocks());
                commands.add("multi");
                commands.add("multiple");
                commands.add("nhiều");
                StringUtil.copyPartialMatches(args[2], commands, completions);
            }
            
            // Tab completion cho loại sự kiện
            if (args[0].equalsIgnoreCase("event") && args[1].equalsIgnoreCase("start") && sender.hasPermission("storage.event")) {
                List<String> eventTypes = new ArrayList<>();
                for (MiningEvent.EventType type : MiningEvent.EventType.values()) {
                    if (type != MiningEvent.EventType.NONE) {
                        eventTypes.add(type.name());
                    }
                }
                StringUtil.copyPartialMatches(args[2], eventTypes, completions);
            }
            
            // Tab completion cho add, remove, set
            if (sender.hasPermission("storage.admin")
                    || sender.hasPermission("storage.admin.add") || sender.hasPermission("storage.admin.remove") || sender.hasPermission("storage.admin.set")) {
                if (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("set")) {
                    if (MineManager.getPluginBlocks().contains(args[1])) {
                        // Sử dụng getAllPlayerNames để hiển thị người chơi online và offline cho lệnh add và set
                        if (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("set")) {
                            StringUtil.copyPartialMatches(args[2], getAllPlayerNames(args[2]), completions);
                        } else {
                            // Đối với lệnh remove, chỉ hiển thị người chơi online
                        Bukkit.getServer().getOnlinePlayers().forEach(player -> commands.add(player.getName()));
                        StringUtil.copyPartialMatches(args[2], commands, completions);
                        }
                    }
                }
            }
            
            // Tab completion cho lệnh max
            if (sender.hasPermission("storage.admin")
                    || sender.hasPermission("storage.admin.max")) {
                if (args[0].equalsIgnoreCase("max")) {
                    StringUtil.copyPartialMatches(args[2], Collections.singleton("<number>"), completions);
                }
            }
        }
        
        if (args.length == 4) {
            // Tab completion cho loại sự kiện thời gian
            if (args[0].equalsIgnoreCase("event") && args[1].equalsIgnoreCase("start") && sender.hasPermission("storage.event")) {
                StringUtil.copyPartialMatches(args[3], Collections.singleton("<thời gian>"), completions);
            }
            
            // Tab completion cho add, remove, set
            if (sender.hasPermission("storage.admin")
                    || sender.hasPermission("storage.admin.add") || sender.hasPermission("storage.admin.remove") || sender.hasPermission("storage.admin.set")) {
                if (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("set")) {
                    if (MineManager.getPluginBlocks().contains(args[1])) {
                        StringUtil.copyPartialMatches(args[3], Collections.singleton("<number>"), completions);
                    }
                }
            }
        }
        
        // Thêm gợi ý cho lệnh lịch sử đầy đủ
        if ((args[0].equalsIgnoreCase("logall") || args[0].equalsIgnoreCase("lichsuall") || args[0].equalsIgnoreCase("historyall"))) {
            if (sender.hasPermission("storage.transfer.others")) {
                return getOnlinePlayerNames(args[1]);
            }
        }
        
        Collections.sort(completions);
        return completions;
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

    private List<String> getOnlinePlayerNames(String partialName) {
        List<String> matchedPlayers = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(partialName.toLowerCase())) {
                matchedPlayers.add(player.getName());
            }
        }
        return matchedPlayers;
    }
    
    /**
     * Lấy danh sách tên người chơi (cả online và offline) từ cơ sở dữ liệu
     * @param partialName Phần đầu của tên người chơi để tìm kiếm
     * @return Danh sách tên người chơi phù hợp
     */
    private List<String> getAllPlayerNames(String partialName) {
        List<String> matchedPlayers = new ArrayList<>();
        
        // Thêm người chơi online
        matchedPlayers.addAll(getOnlinePlayerNames(partialName));
        
        // Thêm người chơi offline đã có dữ liệu
        try {
            Connection conn = null;
            PreparedStatement ps = null;
            ResultSet rs = null;
            
            try {
                conn = Storage.db.getConnection();
                ps = conn.prepareStatement("SELECT player FROM PlayerData WHERE player LIKE ? LIMIT 50");
                ps.setString(1, partialName + "%");
                rs = ps.executeQuery();
                
                while (rs.next()) {
                    String playerName = rs.getString("player");
                    // Chỉ thêm người chơi offline (người chơi online đã được thêm ở trên)
                    if (Bukkit.getPlayer(playerName) == null && !matchedPlayers.contains(playerName)) {
                        matchedPlayers.add(playerName);
                    }
                }
            } finally {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
                if (conn != null) Storage.db.returnConnection(conn);
            }
        } catch (SQLException e) {
            // Bỏ qua lỗi và chỉ trả về người chơi online
            Storage.getStorage().getLogger().warning("Lỗi khi tìm kiếm người chơi trong cơ sở dữ liệu: " + e.getMessage());
        }
        
        return matchedPlayers;
    }

    /**
     * Kiểm tra xem người chơi có thể xem toàn bộ log hay không
     * @param viewerUUID UUID của người xem
     * @param targetPlayer Tên người chơi mục tiêu
     * @return true nếu có thể xem toàn bộ log
     */
    private boolean canViewFullLog(String viewerUUID, String targetPlayer) {
        // Kiểm tra nếu đã hết hạn
        Long expiryTime = logAllExpiryTimes.get(viewerUUID);
        if (expiryTime != null) {
            if (System.currentTimeMillis() > expiryTime) {
                // Đã hết hạn, xóa khỏi danh sách
                viewFullLogPlayers.remove(viewerUUID);
                logAllExpiryTimes.remove(viewerUUID);
                return false;
            }
        }
        
        // Kiểm tra xem người chơi có trong danh sách xem toàn bộ log không
        String storedTarget = viewFullLogPlayers.get(viewerUUID);
        return storedTarget != null && storedTarget.equalsIgnoreCase(targetPlayer);
    }
    
    /**
     * Đặt trạng thái xem toàn bộ log cho người chơi
     * @param viewerUUID UUID của người xem
     * @param targetPlayer Tên người chơi mục tiêu
     */
    private void setViewFullLog(String viewerUUID, String targetPlayer) {
        // Thêm vào danh sách
        viewFullLogPlayers.put(viewerUUID, targetPlayer);
        
        // Thiết lập thời gian hết hạn
        int expirySeconds = File.getConfig().getInt("logall_expiry_time", 600);
        logAllExpiryTimes.put(viewerUUID, System.currentTimeMillis() + (expirySeconds * 1000L));
    }

    /**
     * Lấy danh sách lịch sử chuyển kho đã được lọc theo các tiêu chí
     * @param playerName Tên người chơi
     * @param page Số trang (0-based)
     * @param pageSize Số lượng mục trên mỗi trang
     * @param material Lọc theo loại tài nguyên (có thể null)
     * @param startTime Thời gian bắt đầu tìm kiếm (tính bằng milliseconds)
     * @param endTime Thời gian kết thúc tìm kiếm (tính bằng milliseconds)
     * @param sortOrder Thứ tự sắp xếp ("asc" hoặc "desc")
     * @return Danh sách lịch sử đã lọc
     */
    private List<TransferHistory> getFilteredTransferHistory(String playerName, int page, int pageSize, 
                                                           String material, long startTime, long endTime, String sortOrder) {
        List<TransferHistory> result = new ArrayList<>();
        
        try (java.sql.Connection conn = Storage.db.getConnection()) {
            if (conn == null) {
                Storage.getStorage().getLogger().warning("Không thể kết nối đến cơ sở dữ liệu để lấy lịch sử chuyển kho.");
                return result;
            }
            
            StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append("SELECT * FROM transfer_history WHERE ");
            
            // Điều kiện người chơi
            sqlBuilder.append("(sender = ? OR receiver = ?) ");
            
            // Thêm điều kiện lọc theo loại tài nguyên
            if (material != null && !material.isEmpty()) {
                sqlBuilder.append("AND material = ? ");
            }
            
            // Thêm điều kiện lọc theo thời gian
            if (startTime > 0) {
                // Chuyển timestamp thành chuỗi định dạng cho SQLite
                String startTimeStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(startTime));
                sqlBuilder.append("AND timestamp >= ? ");
            }
            
            if (endTime < System.currentTimeMillis()) {
                // Chuyển timestamp thành chuỗi định dạng cho SQLite
                String endTimeStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(endTime));
                sqlBuilder.append("AND timestamp <= ? ");
            }
            
            // Thứ tự sắp xếp
            if ("asc".equalsIgnoreCase(sortOrder)) {
                sqlBuilder.append("ORDER BY timestamp ASC ");
            } else {
                sqlBuilder.append("ORDER BY timestamp DESC ");
            }
            
            // Phân trang
            sqlBuilder.append("LIMIT ? OFFSET ?");
            String sql = sqlBuilder.toString();
            
            try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                int paramIndex = 1;
                
                // Thiết lập tham số người chơi
                ps.setString(paramIndex++, playerName);
                ps.setString(paramIndex++, playerName);
                
                // Thiết lập tham số lọc theo loại tài nguyên
                if (material != null && !material.isEmpty()) {
                    ps.setString(paramIndex++, material);
                }
                
                // Thiết lập tham số lọc theo thời gian
                if (startTime > 0) {
                    ps.setString(paramIndex++, com.hongminh54.storage.Utils.TransferManager.formatTimestamp(startTime));
                }
                
                if (endTime < System.currentTimeMillis()) {
                    ps.setString(paramIndex++, com.hongminh54.storage.Utils.TransferManager.formatTimestamp(endTime));
                }
                
                // Thiết lập tham số phân trang
                ps.setInt(paramIndex++, pageSize);
                ps.setInt(paramIndex, page * pageSize);
                
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        // Sử dụng TransferManager.parseTimestamp để chuyển đổi timestamp đúng cách
                        TransferHistory transfer = new TransferHistory(
                            rs.getInt("id"),
                            rs.getString("sender"),
                            rs.getString("receiver"),
                            rs.getString("material"),
                            rs.getInt("amount"),
                            com.hongminh54.storage.Utils.TransferManager.parseTimestamp(rs.getString("timestamp"))
                        );
                        result.add(transfer);
                    }
                }
            }
        } catch (Exception e) {
            Storage.getStorage().getLogger().severe("Lỗi khi lấy lịch sử chuyển kho: " + e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }
    
    /**
     * Đếm tổng số lịch sử chuyển kho phù hợp với bộ lọc
     * @param playerName Tên người chơi
     * @param material Lọc theo loại tài nguyên (có thể null)
     * @param startTime Thời gian bắt đầu tìm kiếm (tính bằng milliseconds)
     * @param endTime Thời gian kết thúc tìm kiếm (tính bằng milliseconds)
     * @return Số lượng mục phù hợp với bộ lọc
     */
    private int countFilteredTransferHistory(String playerName, String material, long startTime, long endTime) {
        int count = 0;
        
        try (java.sql.Connection conn = Storage.db.getConnection()) {
            if (conn == null) {
                Storage.getStorage().getLogger().warning("Không thể kết nối đến cơ sở dữ liệu để đếm lịch sử chuyển kho.");
                return 0;
            }
            
            StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append("SELECT COUNT(*) as total FROM transfer_history WHERE ");
            
            // Điều kiện người chơi
            sqlBuilder.append("(sender = ? OR receiver = ?) ");
            
            // Thêm điều kiện lọc theo loại tài nguyên
            if (material != null && !material.isEmpty()) {
                sqlBuilder.append("AND material = ? ");
            }
            
            // Thêm điều kiện lọc theo thời gian
            if (startTime > 0) {
                sqlBuilder.append("AND timestamp >= ? ");
            }
            
            if (endTime < System.currentTimeMillis()) {
                sqlBuilder.append("AND timestamp <= ? ");
            }
            
            String sql = sqlBuilder.toString();
            
            try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                int paramIndex = 1;
                
                // Thiết lập tham số người chơi
                ps.setString(paramIndex++, playerName);
                ps.setString(paramIndex++, playerName);
                
                // Thiết lập tham số lọc theo loại tài nguyên
                if (material != null && !material.isEmpty()) {
                    ps.setString(paramIndex++, material);
                }
                
                // Thiết lập tham số lọc theo thời gian
                if (startTime > 0) {
                    ps.setString(paramIndex++, com.hongminh54.storage.Utils.TransferManager.formatTimestamp(startTime));
                }
                
                if (endTime < System.currentTimeMillis()) {
                    ps.setString(paramIndex++, com.hongminh54.storage.Utils.TransferManager.formatTimestamp(endTime));
                }
                
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        count = rs.getInt("total");
                    }
                }
            }
        } catch (Exception e) {
            Storage.getStorage().getLogger().severe("Lỗi khi đếm lịch sử chuyển kho: " + e.getMessage());
            e.printStackTrace();
        }
        
        return count;
    }
    
    /**
     * Hiển thị thống kê về lịch sử chuyển kho
     * @param player Người chơi xem thống kê
     * @param targetPlayer Tên người chơi cần xem thống kê
     * @param material Lọc theo loại tài nguyên (có thể null)
     * @param startTime Thời gian bắt đầu tìm kiếm (tính bằng milliseconds)
     * @param endTime Thời gian kết thúc tìm kiếm (tính bằng milliseconds)
     */
    private void displayTransferStats(Player player, String targetPlayer, String material, long startTime, long endTime) {
        try (java.sql.Connection conn = Storage.db.getConnection()) {
            if (conn == null) {
                Storage.getStorage().getLogger().warning("Không thể kết nối đến cơ sở dữ liệu để lấy thống kê chuyển kho.");
                return;
            }
            
            // Tính tổng số lượng đã chuyển đi
            long totalSent = 0;
            int totalSentTransactions = 0;
            
            StringBuilder sentSqlBuilder = new StringBuilder();
            sentSqlBuilder.append("SELECT SUM(amount) as total_amount, COUNT(*) as transaction_count FROM transfer_history WHERE sender = ? ");
            
            // Thêm điều kiện lọc theo loại tài nguyên
            if (material != null && !material.isEmpty()) {
                sentSqlBuilder.append("AND material = ? ");
            }
            
            // Thêm điều kiện lọc theo thời gian
            if (startTime > 0) {
                sentSqlBuilder.append("AND timestamp >= ? ");
            }
            
            if (endTime < System.currentTimeMillis()) {
                sentSqlBuilder.append("AND timestamp <= ? ");
            }
            
            try (java.sql.PreparedStatement ps = conn.prepareStatement(sentSqlBuilder.toString())) {
                int paramIndex = 1;
                
                // Thiết lập tham số người chơi
                ps.setString(paramIndex++, targetPlayer);
                
                // Thiết lập tham số lọc theo loại tài nguyên
                if (material != null && !material.isEmpty()) {
                    ps.setString(paramIndex++, material);
                }
                
                // Thiết lập tham số lọc theo thời gian
                if (startTime > 0) {
                    ps.setString(paramIndex++, com.hongminh54.storage.Utils.TransferManager.formatTimestamp(startTime));
                }
                
                if (endTime < System.currentTimeMillis()) {
                    ps.setString(paramIndex++, com.hongminh54.storage.Utils.TransferManager.formatTimestamp(endTime));
                }
                
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        totalSent = rs.getLong("total_amount");
                        totalSentTransactions = rs.getInt("transaction_count");
                    }
                }
            }
            
            // Tính tổng số lượng đã nhận
            long totalReceived = 0;
            int totalReceivedTransactions = 0;
            
            StringBuilder receivedSqlBuilder = new StringBuilder();
            receivedSqlBuilder.append("SELECT SUM(amount) as total_amount, COUNT(*) as transaction_count FROM transfer_history WHERE receiver = ? ");
            
            // Thêm điều kiện lọc theo loại tài nguyên
            if (material != null && !material.isEmpty()) {
                receivedSqlBuilder.append("AND material = ? ");
            }
            
            // Thêm điều kiện lọc theo thời gian
            if (startTime > 0) {
                receivedSqlBuilder.append("AND timestamp >= ? ");
            }
            
            if (endTime < System.currentTimeMillis()) {
                receivedSqlBuilder.append("AND timestamp <= ? ");
            }
            
            try (java.sql.PreparedStatement ps = conn.prepareStatement(receivedSqlBuilder.toString())) {
                int paramIndex = 1;
                
                // Thiết lập tham số người chơi
                ps.setString(paramIndex++, targetPlayer);
                
                // Thiết lập tham số lọc theo loại tài nguyên
                if (material != null && !material.isEmpty()) {
                    ps.setString(paramIndex++, material);
                }
                
                // Thiết lập tham số lọc theo thời gian
                if (startTime > 0) {
                    ps.setString(paramIndex++, com.hongminh54.storage.Utils.TransferManager.formatTimestamp(startTime));
                }
                
                if (endTime < System.currentTimeMillis()) {
                    ps.setString(paramIndex++, com.hongminh54.storage.Utils.TransferManager.formatTimestamp(endTime));
                }
                
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        totalReceived = rs.getLong("total_amount");
                        totalReceivedTransactions = rs.getInt("transaction_count");
                    }
                }
            }
            
            // Hiển thị thống kê
            player.sendMessage(Chat.colorize("&e&l≫ Thống kê giao dịch:"));
            
            // Nếu có lọc theo loại tài nguyên
            if (material != null && !material.isEmpty()) {
                String displayName = File.getConfig().getString("items." + material, material);
                player.sendMessage(Chat.colorize("&eLoại tài nguyên: &f" + displayName));
            }
            
            // Hiển thị số giao dịch
            player.sendMessage(Chat.colorize("&7Tổng giao dịch: &f" + (totalSentTransactions + totalReceivedTransactions)));
            
            if (totalSentTransactions > 0) {
                player.sendMessage(Chat.colorize("&7Đã chuyển đi: &f" + totalSent + " &7tài nguyên &8(" + totalSentTransactions + " giao dịch)"));
            }
            
            if (totalReceivedTransactions > 0) {
                player.sendMessage(Chat.colorize("&7Đã nhận: &f" + totalReceived + " &7tài nguyên &8(" + totalReceivedTransactions + " giao dịch)"));
            }
            
            // Hiển thị cân bằng
            long balance = totalReceived - totalSent;
            String balancePrefix = balance >= 0 ? "&a+" : "&c";
            player.sendMessage(Chat.colorize("&7Cân bằng: " + balancePrefix + balance + " &7tài nguyên"));
        } catch (Exception e) {
            Storage.getStorage().getLogger().severe("Lỗi khi lấy thống kê chuyển kho: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Tạo lệnh tìm kiếm lịch sử chuyển kho với tất cả tham số
     * @param targetPlayer Tên người chơi
     * @param page Số trang (1-based)
     * @param materialFilter Lọc theo loại tài nguyên (có thể null)
     * @param startTime Thời gian bắt đầu tìm kiếm (tính bằng milliseconds)
     * @param endTime Thời gian kết thúc tìm kiếm (tính bằng milliseconds)
     * @param sortOrder Thứ tự sắp xếp ("asc" hoặc "desc")
     * @param showStats Hiển thị thống kê hay không
     * @return Chuỗi lệnh hoàn chỉnh
     */
    private String buildLogCommand(String targetPlayer, int page, String materialFilter, long startTime, long endTime, String sortOrder, boolean showStats) {
        StringBuilder cmdBuilder = new StringBuilder("/kho log");
        
        // Thêm tên người chơi nếu khác với người gửi
        cmdBuilder.append(" ").append(targetPlayer);
        
        // Thêm số trang
        cmdBuilder.append(" ").append(page);
        
        // Thêm lọc theo loại tài nguyên
        if (materialFilter != null && !materialFilter.isEmpty()) {
            cmdBuilder.append(" -m:").append(materialFilter);
        }
        
        // Thêm lọc theo thời gian
        if (startTime > 0) {
            // Tính số ngày từ hiện tại
            int daysBefore = (int) ((System.currentTimeMillis() - startTime) / (24 * 60 * 60 * 1000L));
            cmdBuilder.append(" -from:").append(daysBefore);
        }
        
        if (endTime < System.currentTimeMillis()) {
            // Tính số ngày từ hiện tại
            int daysBefore = (int) ((System.currentTimeMillis() - endTime) / (24 * 60 * 60 * 1000L));
            cmdBuilder.append(" -to:").append(daysBefore);
        }
        
        // Thêm thứ tự sắp xếp
        if ("asc".equalsIgnoreCase(sortOrder)) {
            cmdBuilder.append(" -asc");
        } else if ("desc".equalsIgnoreCase(sortOrder)) {
            cmdBuilder.append(" -desc");
        }
        
        // Thêm hiển thị thống kê
        if (showStats) {
            cmdBuilder.append(" -stats");
        }
        
        return cmdBuilder.toString();
    }
    
    /**
     * Hiển thị hướng dẫn sử dụng tìm kiếm nâng cao cho lệnh /kho log
     * @param player Người chơi nhận hướng dẫn
     */
    private void displayLogHelp(Player player) {
        player.sendMessage(Chat.colorize("&e&l≫ Hướng dẫn tìm kiếm lịch sử chuyển kho:"));
        player.sendMessage(Chat.colorize("&e/kho log [tên người chơi] [trang] [các tùy chọn]"));
        player.sendMessage(Chat.colorize("&7Các tùy chọn tìm kiếm:"));
        player.sendMessage(Chat.colorize("&7• &f-m:<tên vật phẩm> &7- Lọc theo loại tài nguyên"));
        player.sendMessage(Chat.colorize("&7• &f-from:<số ngày> &7- Lọc từ số ngày trước"));
        player.sendMessage(Chat.colorize("&7• &f-to:<số ngày> &7- Lọc đến số ngày trước"));
        player.sendMessage(Chat.colorize("&7• &f-stats &7- Hiển thị thống kê giao dịch"));
        player.sendMessage(Chat.colorize("&7• &f-asc &7- Sắp xếp từ cũ đến mới"));
        player.sendMessage(Chat.colorize("&7• &f-desc &7- Sắp xếp từ mới đến cũ (mặc định)"));
        player.sendMessage(Chat.colorize("&e&l≫ Ví dụ:"));
        player.sendMessage(Chat.colorize("&7/kho log Player1 -m:DIAMOND -from:7 -stats"));
        player.sendMessage(Chat.colorize("&7(Xem lịch sử chuyển kim cương của Player1 trong 7 ngày qua và hiển thị thống kê)"));
    }

    /**
     * Xử lý lệnh /kho add cho cả người chơi online và offline
     * @param c Người gửi lệnh
     * @param args Các tham số lệnh
     * @param targetPlayer Người chơi mục tiêu (có thể null nếu offline)
     * @param amount Số lượng
     */
    private void handleAddCommand(CommandSender c, String[] args, Player targetPlayer, int amount) {
        String targetName = args[2];
        String material = args[1];
        
        if (targetPlayer != null) {
            // Người chơi online - Sử dụng phương thức hiện có
            Storage.getStorage().getLogger().info("Thực hiện lệnh /kho add: " + material + " cho " + targetPlayer.getName() + " số lượng " + amount);
            
            // Kiểm tra nếu tài nguyên không tồn tại trong kho của người chơi, thì khởi tạo
            if (!MineManager.hasPlayerBlock(targetPlayer, material)) {
                Storage.getStorage().getLogger().info("Khởi tạo tài nguyên " + material + " cho " + targetPlayer.getName());
                MineManager.setBlock(targetPlayer, material, 0);
            }
            
            // Thực hiện thêm tài nguyên
            if (MineManager.addBlockAmount(targetPlayer, material, amount)) {
                c.sendMessage(Chat.colorize(File.getMessage().getString("admin.add_material_amount")).replace("#amount#", Integer.toString(amount)).replace("#material#", material).replace("#player#", targetPlayer.getName()));
                targetPlayer.sendMessage(Chat.colorize(File.getMessage().getString("user.add_material_amount")).replace("#amount#", Integer.toString(amount)).replace("#material#", material).replace("#player#", c.getName()));
                
                // Ghi log xác nhận số lượng sau khi thêm
                int currentAmount = MineManager.getPlayerBlock(targetPlayer, material);
                Storage.getStorage().getLogger().info("Đã thêm thành công: " + material + " cho " + targetPlayer.getName() + ", số lượng hiện tại: " + currentAmount);
                
                // Lưu dữ liệu ngay lập tức
                MineManager.savePlayerDataAsync(targetPlayer);
            } else {
                c.sendMessage(Chat.colorize("&c❌ Không thể thêm tài nguyên. Có thể do kho đã đầy hoặc lỗi dữ liệu."));
                Storage.getStorage().getLogger().warning("Không thể thêm tài nguyên " + material + " cho " + targetPlayer.getName() + ", số lượng: " + amount);
            }
        } else {
            // Người chơi offline - Cập nhật trực tiếp trong cơ sở dữ liệu
            try {
                Storage.getStorage().getLogger().info("Thực hiện lệnh /kho add cho người chơi offline: " + material + " cho " + targetName + " số lượng " + amount);
                
                // Lấy dữ liệu từ database
                PlayerData playerData = Storage.db.getData(targetName);
                if (playerData == null) {
                    c.sendMessage(Chat.colorize("&c❌ Không tìm thấy dữ liệu người chơi: " + targetName));
                    return;
                }
                
                // Chuyển đổi dữ liệu chuỗi JSON thành danh sách
                List<String> blockData = MineManager.convertOnlineData(playerData.getData());
                
                // Tìm và cập nhật tài nguyên trong danh sách
                boolean foundResource = false;
                int maxStorage = playerData.getMax();
                Map<String, Integer> blockMap = new HashMap<>();
                
                // Chuyển đổi danh sách thành map để dễ xử lý
                for (String blockEntry : blockData) {
                    String[] parts = blockEntry.split(";");
                    if (parts.length >= 2) {
                        String blockMaterial = parts[0];
                        int blockAmount = 0;
                        try {
                            blockAmount = Integer.parseInt(parts[1]);
                        } catch (NumberFormatException e) {
                            // Bỏ qua lỗi, giữ số lượng là 0
                        }
                        
                        blockMap.put(blockMaterial, blockAmount);
                    }
                }
                
                // Cập nhật số lượng tài nguyên
                if (blockMap.containsKey(material)) {
                    foundResource = true;
                    int currentAmount = blockMap.get(material);
                    int newAmount = currentAmount + amount;
                    
                    if (newAmount <= maxStorage) {
                        blockMap.put(material, newAmount);
                        c.sendMessage(Chat.colorize("&a✓ Đã thêm &e" + amount + " " + material + "&a vào kho của &e" + targetName + "&a. Số lượng hiện tại: &e" + newAmount));
                    } else {
                        blockMap.put(material, maxStorage);
                        c.sendMessage(Chat.colorize("&e⚠ Kho đã đầy. Đã đặt " + material + " cho " + targetName + " thành giới hạn tối đa: " + maxStorage));
                    }
                } else {
                    // Nếu không tìm thấy tài nguyên, thêm mới
                    blockMap.put(material, Math.min(amount, maxStorage));
                    c.sendMessage(Chat.colorize("&a✓ Đã thêm mới &e" + Math.min(amount, maxStorage) + " " + material + "&a vào kho của &e" + targetName));
                }
                
                // Chuyển Map thành chuỗi JSON đúng định dạng
                StringBuilder mapAsString = new StringBuilder("{");
                for (Map.Entry<String, Integer> entry : blockMap.entrySet()) {
                    mapAsString.append(entry.getKey()).append("=").append(entry.getValue()).append(", ");
                }
                
                // Xóa dấu phẩy cuối cùng và đóng chuỗi JSON
                if (mapAsString.length() > 1) {
                    mapAsString.delete(mapAsString.length() - 2, mapAsString.length());
                }
                mapAsString.append("}");
                
                // Tạo đối tượng PlayerData mới với dữ liệu đã cập nhật
                PlayerData updatedData = new PlayerData(
                    targetName,
                    mapAsString.toString(),
                    maxStorage,
                    playerData.getStatsData()
                );
                
                // Cập nhật vào cơ sở dữ liệu
                Storage.db.updateTable(updatedData);
                Storage.getStorage().getLogger().info("Đã cập nhật thành công kho của người chơi offline: " + targetName);
            } catch (Exception e) {
                c.sendMessage(Chat.colorize("&c❌ Đã xảy ra lỗi khi cập nhật kho: " + e.getMessage()));
                Storage.getStorage().getLogger().severe("Lỗi khi cập nhật kho người chơi offline " + targetName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Xử lý lệnh /kho set cho cả người chơi online và offline
     * @param c Người gửi lệnh
     * @param args Các tham số lệnh
     * @param targetPlayer Người chơi mục tiêu (có thể null nếu offline)
     * @param amount Số lượng
     */
    private void handleSetCommand(CommandSender c, String[] args, Player targetPlayer, int amount) {
        String targetName = args[2];
        String material = args[1];
        
        if (targetPlayer != null) {
            // Người chơi online - Sử dụng phương thức hiện có
            Storage.getStorage().getLogger().info("Thực hiện lệnh /kho set: " + material + " cho " + targetPlayer.getName() + " thành " + amount);
            
            // Thực hiện đặt tài nguyên
            MineManager.setBlock(targetPlayer, material, amount);
            c.sendMessage(Chat.colorize(File.getMessage().getString("admin.set_material_amount")).replace("#amount#", Integer.toString(amount)).replace("#material#", material).replace("#player#", targetPlayer.getName()));
            targetPlayer.sendMessage(Chat.colorize(File.getMessage().getString("user.set_material_amount")).replace("#amount#", Integer.toString(amount)).replace("#material#", material).replace("#player#", c.getName()));
            
            // Ghi log xác nhận số lượng sau khi đặt
            int currentAmount = MineManager.getPlayerBlock(targetPlayer, material);
            Storage.getStorage().getLogger().info("Đã đặt thành công: " + material + " cho " + targetPlayer.getName() + ", số lượng hiện tại: " + currentAmount);
            
            // Lưu dữ liệu ngay lập tức
            MineManager.savePlayerDataAsync(targetPlayer);
        } else {
            // Người chơi offline - Cập nhật trực tiếp trong cơ sở dữ liệu
            try {
                Storage.getStorage().getLogger().info("Thực hiện lệnh /kho set cho người chơi offline: " + material + " cho " + targetName + " thành " + amount);
                
                // Lấy dữ liệu từ database
                PlayerData playerData = Storage.db.getData(targetName);
                if (playerData == null) {
                    c.sendMessage(Chat.colorize("&c❌ Không tìm thấy dữ liệu người chơi: " + targetName));
                    return;
                }
                
                // Chuyển đổi dữ liệu chuỗi JSON thành danh sách
                List<String> blockData = MineManager.convertOnlineData(playerData.getData());
                
                // Tìm và cập nhật tài nguyên trong danh sách
                int maxStorage = playerData.getMax();
                Map<String, Integer> blockMap = new HashMap<>();
                
                // Chuyển đổi danh sách thành map để dễ xử lý
                for (String blockEntry : blockData) {
                    String[] parts = blockEntry.split(";");
                    if (parts.length >= 2) {
                        String blockMaterial = parts[0];
                        int blockAmount = 0;
                        try {
                            blockAmount = Integer.parseInt(parts[1]);
                        } catch (NumberFormatException e) {
                            // Bỏ qua lỗi, giữ số lượng là 0
                        }
                        blockMap.put(blockMaterial, blockAmount);
                    }
                }
                
                // Giới hạn số lượng không vượt quá maxStorage
                int finalAmount = Math.min(amount, maxStorage);
                
                // Cập nhật số lượng hoặc thêm mới
                blockMap.put(material, finalAmount);
                
                if (finalAmount < amount) {
                    c.sendMessage(Chat.colorize("&e⚠ Vượt quá giới hạn kho. Đã đặt " + material + " cho " + targetName + " thành giá trị tối đa: " + maxStorage));
                } else {
                    c.sendMessage(Chat.colorize("&a✓ Đã đặt &e" + material + "&a cho &e" + targetName + "&a thành &e" + finalAmount));
                }
                
                // Chuyển Map thành chuỗi JSON đúng định dạng
                StringBuilder mapAsString = new StringBuilder("{");
                for (Map.Entry<String, Integer> entry : blockMap.entrySet()) {
                    mapAsString.append(entry.getKey()).append("=").append(entry.getValue()).append(", ");
                }
                
                // Xóa dấu phẩy cuối cùng và đóng chuỗi JSON
                if (mapAsString.length() > 1) {
                    mapAsString.delete(mapAsString.length() - 2, mapAsString.length());
                }
                mapAsString.append("}");
                
                // Tạo đối tượng PlayerData mới với dữ liệu đã cập nhật
                PlayerData updatedData = new PlayerData(
                    targetName,
                    mapAsString.toString(),
                    maxStorage,
                    playerData.getStatsData()
                );
                
                // Cập nhật vào cơ sở dữ liệu
                Storage.db.updateTable(updatedData);
                Storage.getStorage().getLogger().info("Đã cập nhật thành công kho của người chơi offline: " + targetName);
            } catch (Exception e) {
                c.sendMessage(Chat.colorize("&c❌ Đã xảy ra lỗi khi cập nhật kho: " + e.getMessage()));
                Storage.getStorage().getLogger().severe("Lỗi khi cập nhật kho người chơi offline " + targetName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}