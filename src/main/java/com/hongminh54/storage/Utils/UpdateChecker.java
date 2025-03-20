package com.hongminh54.storage.Utils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import com.hongminh54.storage.Storage;

public class UpdateChecker implements Listener {

    private final String GITHUB_REPO = "hongminh54/KhoKhoangSan"; 
    private final String GITHUB_API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
    private final Storage plugin;
    private final String pluginVersion;
    private String githubVersion;
    private boolean updateAvailable;
    private boolean devBuildVersion;
    private String downloadUrl;
    private final Map<UUID, Boolean> pendingDownloads = new HashMap<>();

    public UpdateChecker(@NotNull Storage storage) {
        plugin = storage;
        pluginVersion = storage.getDescription().getVersion();
    }

    public String getGithubVersion() {
        return githubVersion;
    }

    public void fetch() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (com.hongminh54.storage.Utils.File.getConfig().getBoolean("check_update")) {
                try {
                    HttpsURLConnection con = (HttpsURLConnection) new URL(GITHUB_API_URL).openConnection();
                    con.setRequestMethod("GET");
                    con.setRequestProperty("Accept", "application/vnd.github.v3+json");
                    
                    StringBuilder response = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                    }
                    
                    // Lấy tag_name từ JSON response (ví dụ: "tag_name":"v1.0.0")
                    String jsonResponse = response.toString();
                    int tagIndex = jsonResponse.indexOf("\"tag_name\"");
                    if (tagIndex != -1) {
                        int startIndex = jsonResponse.indexOf(":", tagIndex) + 2;
                        int endIndex = jsonResponse.indexOf("\"", startIndex + 1);
                        String tagName = jsonResponse.substring(startIndex, endIndex);
                        // Loại bỏ 'v' nếu có (ví dụ: v1.0.0 -> 1.0.0)
                        githubVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                    }
                    
                    // Tìm URL tải xuống
                    int browserDownloadUrlIndex = jsonResponse.indexOf("\"browser_download_url\"");
                    if (browserDownloadUrlIndex != -1) {
                        int startIndex = jsonResponse.indexOf(":", browserDownloadUrlIndex) + 2;
                        int endIndex = jsonResponse.indexOf("\"", startIndex + 1);
                        downloadUrl = jsonResponse.substring(startIndex, endIndex);
                    } else {
                        // Nếu không tìm thấy browser_download_url, sử dụng URL chung của bản phát hành
                        downloadUrl = "https://github.com/" + GITHUB_REPO + "/releases/latest";
                    }
                } catch (Exception ex) {
                    plugin.getLogger().info("Không thể kiểm tra cập nhật từ GitHub.");
                    return;
                }

                if (githubVersion == null || githubVersion.isEmpty()) {
                    return;
                }

                updateAvailable = githubIsNewer();
                devBuildVersion = devBuildIsNewer();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (devBuildVersion) {
                        plugin.getLogger().warning("Bạn đang sử dụng phiên bản DevBuild của Storage Plugin");
                        plugin.getLogger().warning("Hầu hết các tính năng trong DevBuild đã sửa lỗi và có tính năng mới cho phiên bản tiếp theo, và có thể bao gồm các vấn đề khác");
                        plugin.getLogger().warning("Vì vậy, nếu bạn gặp bất kỳ vấn đề nào, vui lòng vào Discord của tôi và báo cáo cho Danh!");
                    }
                    if (updateAvailable) {
                        plugin.getLogger().warning("Một bản cập nhật cho Storage (v" + getGithubVersion() + ") đã có sẵn tại:");
                        plugin.getLogger().warning("https://github.com/" + GITHUB_REPO + "/releases/latest");
                        plugin.getLogger().warning("Bạn đang sử dụng phiên bản v" + pluginVersion);
                        plugin.getLogger().warning("Nếu phiên bản plugin của bạn cao hơn phiên bản GitHub, bạn có thể bỏ qua thông báo này");
                        Bukkit.getPluginManager().registerEvents(this, plugin);
                    } else {
                        plugin.getLogger().info("Đây là phiên bản mới nhất của Storage Plugin");
                    }
                });
            }
        });
    }

    private boolean githubIsNewer() {
        if (githubVersion == null || githubVersion.isEmpty() || !githubVersion.matches("[0-9].[0-9].[0-9]")) {
            return false;
        }

        int[] plV = toReadable(pluginVersion);
        int[] ghV = toReadable(githubVersion);

        if (plV == null || ghV == null) return false;

        if (plV[0] < ghV[0]) {
            return true;
        }
        if ((plV[1] < ghV[1])) {
            return true;
        }
        return plV[2] < ghV[2];
    }

    private boolean devBuildIsNewer() {
        if (githubVersion == null || githubVersion.isEmpty() || !githubVersion.matches("[0-9].[0-9].[0-9]")) {
            return false;
        }

        int[] plV = toReadable(pluginVersion);
        int[] ghV = toReadable(githubVersion);

        if (plV == null || ghV == null) return false;

        if (plV[0] > ghV[0]) {
            return true;
        }
        if ((plV[1] > ghV[1])) {
            return true;
        }
        return plV[2] > ghV[2];
    }

    private int[] toReadable(@NotNull String version) {
        if (version.endsWith("-SNAPSHOT")) {
            version = version.split("-SNAPSHOT")[0];
        }
        return Arrays.stream(version.split("\\.")).mapToInt(Integer::parseInt).toArray();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(@NotNull PlayerJoinEvent e) {
        if (updateAvailable) {
            Player player = e.getPlayer();
            if (player.hasPermission("storage.admin")) {
                player.sendMessage(ChatColor.GREEN + String.format("Đã có bản cập nhật cho Storage tại %s", "https://github.com/" + GITHUB_REPO + "/releases/latest"));
                player.sendMessage(ChatColor.GREEN + String.format("Bạn đang sử dụng phiên bản %s", pluginVersion));
                player.sendMessage(ChatColor.GREEN + "Nếu phiên bản plugin của bạn cao hơn phiên bản GitHub, bạn có thể bỏ qua thông báo này");
                
                // Chỉ hiển thị nút tải xuống nếu tìm thấy URL tải xuống
                if (downloadUrl != null && !downloadUrl.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "Bạn có muốn tải xuống phiên bản mới nhất không?");
                    player.sendMessage(ChatColor.GREEN + "Gõ " + ChatColor.WHITE + "/storage update" + ChatColor.GREEN + " để tải xuống bản cập nhật");
                    
                    // Lưu trạng thái chờ tải xuống
                    pendingDownloads.put(player.getUniqueId(), true);
                }
            }
        }
    }
    
    /**
     * Kiểm tra xem người chơi có đang chờ tải xuống cập nhật không
     * @param playerUUID UUID của người chơi
     * @return true nếu người chơi có yêu cầu tải xuống đang chờ
     */
    public boolean hasPendingDownload(UUID playerUUID) {
        return pendingDownloads.getOrDefault(playerUUID, false);
    }
    
    /**
     * Xóa yêu cầu tải xuống đang chờ của người chơi
     * @param playerUUID UUID của người chơi
     */
    public void removePendingDownload(UUID playerUUID) {
        pendingDownloads.remove(playerUUID);
    }
    
    /**
     * Tải xuống phiên bản mới nhất của plugin
     * @param player Người chơi yêu cầu tải xuống
     */
    public void downloadUpdate(Player player) {
        if (!updateAvailable || downloadUrl == null || downloadUrl.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Không có bản cập nhật nào để tải xuống.");
            return;
        }
        
        // Kiểm tra quyền hạn
        if (!player.hasPermission("storage.admin")) {
            player.sendMessage(ChatColor.RED + "Bạn không có quyền cập nhật plugin.");
            return;
        }
        
        player.sendMessage(ChatColor.YELLOW + "Đang tải xuống phiên bản mới nhất của Storage...");
        
        // Tạo tác vụ bất đồng bộ để tải xuống
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Tạo kết nối đến URL tải xuống
                    HttpsURLConnection connection = (HttpsURLConnection) new URL(downloadUrl).openConnection();
                    connection.setRequestProperty("Accept", "application/octet-stream");
                    
                    // Xác định tên file từ URL
                    String fileName;
                    String disposition = connection.getHeaderField("Content-Disposition");
                    if (disposition != null && disposition.contains("filename=")) {
                        fileName = disposition.substring(disposition.indexOf("filename=") + 9).replace("\"", "");
                    } else {
                        fileName = "Storage-" + githubVersion + ".jar";
                    }
                    
                    // Đường dẫn đến thư mục plugins
                    Path pluginsDir = Bukkit.getUpdateFolderFile().toPath().getParent();
                    if (!Files.exists(pluginsDir)) {
                        Files.createDirectories(pluginsDir);
                    }
                    
                    // Tạo đường dẫn đến file mới trong thư mục updates
                    Path updateFolder = Paths.get(pluginsDir.toString(), "update");
                    if (!Files.exists(updateFolder)) {
                        Files.createDirectories(updateFolder);
                    }
                    
                    Path outputPath = Paths.get(updateFolder.toString(), fileName);
                    
                    // Tải xuống file
                    try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                         FileOutputStream out = new FileOutputStream(outputPath.toFile())) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                    
                    // Gửi thông báo hoàn thành cho người chơi
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            player.sendMessage(ChatColor.GREEN + "Tải xuống hoàn tất! Đang cài đặt bản cập nhật...");
                            
                            // Tìm file plugin hiện tại
                            File currentPluginFile = null;
                            for (File file : pluginsDir.toFile().listFiles()) {
                                if (file.getName().startsWith("KhoKhoangSan") && file.getName().endsWith(".jar")) {
                                    currentPluginFile = file;
                                    break;
                                }
                            }
                            
                            if (currentPluginFile != null) {
                                try {
                                    // Sao chép file mới vào thư mục plugins
                                    Files.copy(outputPath, currentPluginFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                    
                                    player.sendMessage(ChatColor.GREEN + "Cài đặt bản cập nhật thành công!");
                                    player.sendMessage(ChatColor.YELLOW + "Vui lòng khởi động lại máy chủ để áp dụng bản cập nhật.");
                                    
                                    // Xóa file tạm trong thư mục update
                                    Files.delete(outputPath);
                                    
                                    // Xóa yêu cầu đang chờ
                                    removePendingDownload(player.getUniqueId());
                                } catch (IOException e) {
                                    player.sendMessage(ChatColor.RED + "Không thể cài đặt bản cập nhật: " + e.getMessage());
                                    plugin.getLogger().severe("Không thể cài đặt bản cập nhật: " + e.getMessage());
                                    e.printStackTrace();
                                }
                            } else {
                                player.sendMessage(ChatColor.RED + "Không tìm thấy file plugin hiện tại!");
                                plugin.getLogger().severe("Không tìm thấy file plugin hiện tại!");
                            }
                        }
                    }.runTask(plugin);
                    
                } catch (Exception e) {
                    // Xử lý lỗi và thông báo
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            player.sendMessage(ChatColor.RED + "Không thể tải xuống bản cập nhật: " + e.getMessage());
                            plugin.getLogger().severe("Không thể tải xuống bản cập nhật: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(plugin);
    }
}