package com.hongminh54.storage.CMD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import com.hongminh54.storage.API.CMDBase;
import com.hongminh54.storage.Database.PlayerData;
import com.hongminh54.storage.GUI.LeaderboardGUI;
import com.hongminh54.storage.GUI.PersonalStorage;
import com.hongminh54.storage.GUI.StatsGUI;
import com.hongminh54.storage.Manager.ItemManager;
import com.hongminh54.storage.Manager.MineManager;
import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.Chat;
import com.hongminh54.storage.Utils.File;
import com.hongminh54.storage.Utils.LeaderboardManager;
import com.hongminh54.storage.Utils.Number;
import com.hongminh54.storage.Utils.StatsManager;
import com.hongminh54.storage.Utils.UpdateChecker;

public class StorageCMD extends CMDBase {
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
                        LeaderboardGUI gui = new LeaderboardGUI(p);
                        p.openInventory(gui.getInventory());
                    } catch (Exception e) {
                        Storage.getStorage().getLogger().severe("Lỗi khi mở bảng xếp hạng: " + e.getMessage());
                        e.printStackTrace();
                        p.sendMessage(Chat.colorizewp("&cKhông thể mở bảng xếp hạng. Vui lòng thử lại sau."));
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
                    int affectedRows = LeaderboardManager.resetLeaderboard();
                    String message = Chat.colorize("&aĐã reset bảng xếp hạng thành công! &7(" + affectedRows + " người chơi)");
                    c.sendMessage(message);
                    String broadcastMessage = Chat.colorize("&c&l[Kho Khoáng Sản] &eBảng xếp hạng đã được reset bởi &f" + c.getName());
                    Bukkit.broadcastMessage(broadcastMessage);
                } catch (Exception e) {
                    String errorMessage = Chat.colorize("&cKhông thể reset bảng xếp hạng. Vui lòng kiểm tra console để biết thêm chi tiết.");
                    c.sendMessage(errorMessage);
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
                    if (p != null) {
                        int number = Number.getInteger(args[3]);
                        if (number >= 0) {
                            if (args[0].equalsIgnoreCase("add")) {
                                if (c.hasPermission("storage.admin")
                                        || c.hasPermission("storage.admin.add")) {
                                    if (MineManager.addBlockAmount(p, args[1], number)) {
                                        c.sendMessage(Chat.colorize(File.getMessage().getString("admin.add_material_amount")).replace("#amount#", args[3]).replace("#material#", args[1]).replace("#player#", p.getName()));
                                        p.sendMessage(Chat.colorize(File.getMessage().getString("user.add_material_amount")).replace("#amount#", args[3]).replace("#material#", args[1]).replace("#player#", c.getName()));
                                    }
                                }
                            }
                            if (args[0].equalsIgnoreCase("remove")) {
                                if (c.hasPermission("storage.admin") || c.hasPermission("storage.admin.remove")) {
                                    if (MineManager.removeBlockAmount(p, args[1], number)) {
                                        c.sendMessage(Chat.colorize(File.getMessage().getString("admin.remove_material_amount")).replace("#amount#", args[3]).replace("#material#", args[1]).replace("#player#", p.getName()));
                                        p.sendMessage(Chat.colorize(File.getMessage().getString("user.remove_material_amount")).replace("#amount#", args[3]).replace("#material#", args[1]).replace("#player#", c.getName()));
                                    }
                                }
                            }
                            if (args[0].equalsIgnoreCase("set")) {
                                if (c.hasPermission("storage.admin") || c.hasPermission("storage.admin.set")) {
                                    MineManager.setBlock(p, args[1], number);
                                    c.sendMessage(Chat.colorize(File.getMessage().getString("admin.set_material_amount")).replace("#amount#", args[3]).replace("#material#", args[1]).replace("#player#", p.getName()));
                                    p.sendMessage(Chat.colorize(File.getMessage().getString("user.set_material_amount")).replace("#amount#", args[3]).replace("#material#", args[1]).replace("#player#", c.getName()));
                                }
                            }
                        }
                    }
                }
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
            if (sender.hasPermission("storage.admin")) {
                commands.add("add");
                commands.add("remove");
                commands.add("set");
                commands.add("reload");
                commands.add("max");
                commands.add("update");
                commands.add("resetleaderboard");
                commands.add("resetstats");
            } else {
                if (sender.hasPermission("storage.admin.add")) commands.add("add");
                if (sender.hasPermission("storage.admin.remove")) commands.add("remove");
                if (sender.hasPermission("storage.admin.set")) commands.add("set");
                if (sender.hasPermission("storage.admin.reload")) commands.add("reload");
                if (sender.hasPermission("storage.admin.max")) commands.add("max");
                if (sender.hasPermission("storage.admin.resetleaderboard")) commands.add("resetleaderboard");
                if (sender.hasPermission("storage.admin.resetstats")) commands.add("resetstats");
            }

            StringUtil.copyPartialMatches(args[0], commands, completions);
        }
        if (args.length == 2) {
            if (sender.hasPermission("storage.admin")) {
                if (args[0].equalsIgnoreCase("resetstats")) {
                    // Chỉ hiển thị tab completion cho người chơi online
                    Bukkit.getServer().getOnlinePlayers().forEach(player -> commands.add(player.getName()));
                    StringUtil.copyPartialMatches(args[1], commands, completions);
                }
            }
            if (sender.hasPermission("storage.admin")
                    || sender.hasPermission("storage.admin.add") || sender.hasPermission("storage.admin.remove") || sender.hasPermission("storage.admin.set")) {
                if (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("set")) {
                    if (commands.addAll(MineManager.getPluginBlocks())) {
                        StringUtil.copyPartialMatches(args[1], commands, completions);
                    }
                }
            }
            if (sender.hasPermission("storage.admin")
                    || sender.hasPermission("storage.admin.max")) {
                if (args[0].equalsIgnoreCase("max")) {
                    Bukkit.getServer().getOnlinePlayers().forEach(player -> commands.add(player.getName()));
                    StringUtil.copyPartialMatches(args[1], commands, completions);
                }
            }
        }
        if (args.length == 3) {
            if (sender.hasPermission("storage.admin")
                    || sender.hasPermission("storage.admin.add") || sender.hasPermission("storage.admin.remove") || sender.hasPermission("storage.admin.set")) {
                if (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("set")) {
                    if (MineManager.getPluginBlocks().contains(args[1])) {
                        Bukkit.getServer().getOnlinePlayers().forEach(player -> commands.add(player.getName()));
                        StringUtil.copyPartialMatches(args[2], commands, completions);
                    }
                }
            }
            if (sender.hasPermission("storage.admin")
                    || sender.hasPermission("storage.admin.max")) {
                if (args[0].equalsIgnoreCase("max")) {
                    StringUtil.copyPartialMatches(args[2], Collections.singleton("<number>"), completions);
                }
            }
        }
        if (args.length == 4) {
            if (sender.hasPermission("storage.admin")
                    || sender.hasPermission("storage.admin.add") || sender.hasPermission("storage.admin.remove") || sender.hasPermission("storage.admin.set")) {
                if (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("set")) {
                    if (MineManager.getPluginBlocks().contains(args[1])) {
                        StringUtil.copyPartialMatches(args[3], Collections.singleton("<number>"), completions);
                    }
                }
            }
        }

        Collections.sort(completions);
        return completions;
    }
}
