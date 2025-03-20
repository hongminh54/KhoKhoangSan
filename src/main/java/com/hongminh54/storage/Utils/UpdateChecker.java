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
import java.util.logging.Level;

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
    private String changelog;
    private final Map<UUID, Boolean> pendingDownloads = new HashMap<>();

    public UpdateChecker(@NotNull Storage storage) {
        plugin = storage;
        pluginVersion = storage.getDescription().getVersion();
    }

    public String getGithubVersion() {
        return githubVersion;
    }
    
    public String getChangelog() {
        return changelog != null ? changelog : "Không có thông tin changelog.";
    }

    public void fetch() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (com.hongminh54.storage.Utils.File.getConfig().getBoolean("check_update")) {
                try {
                    HttpsURLConnection con = (HttpsURLConnection) new URL(GITHUB_API_URL).openConnection();
                    con.setRequestMethod("GET");
                    con.setRequestProperty("Accept", "application/vnd.github.v3+json");
                    con.setConnectTimeout(10000);
                    con.setReadTimeout(10000);
                    
                    int responseCode = con.getResponseCode();
                    if (responseCode != 200) {
                        plugin.getLogger().warning("Không thể kiểm tra cập nhật: Mã phản hồi HTTP " + responseCode);
                        return;
                    }
                    
                    StringBuilder response = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                    }
                    
                    String jsonResponse = response.toString();
                    
                    int tagIndex = jsonResponse.indexOf("\"tag_name\"");
                    if (tagIndex != -1) {
                        int startIndex = jsonResponse.indexOf(":", tagIndex) + 2;
                        int endIndex = jsonResponse.indexOf("\"", startIndex + 1);
                        String tagName = jsonResponse.substring(startIndex, endIndex);
                        githubVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                    } else {
                        plugin.getLogger().warning("Không tìm thấy thông tin phiên bản trong phản hồi GitHub.");
                        return;
                    }
                    
                    int bodyIndex = jsonResponse.indexOf("\"body\"");
                    if (bodyIndex != -1) {
                        int startIndex = jsonResponse.indexOf(":", bodyIndex) + 2;
                        int endIndex = jsonResponse.indexOf("\"", startIndex + 1);
                        
                        while (jsonResponse.indexOf("\\n", endIndex) != -1 && 
                              jsonResponse.indexOf("\\n", endIndex) < jsonResponse.indexOf("\"", endIndex + 1)) {
                            endIndex = jsonResponse.indexOf("\"", endIndex + 1);
                        }
                        
                        if (startIndex < endIndex) {
                            changelog = jsonResponse.substring(startIndex, endIndex)
                                    .replace("\\r\\n", "\n")
                                    .replace("\\n", "\n")
                                    .replace("\\\"", "\"");
                        }
                    }
                    
                    int browserDownloadUrlIndex = jsonResponse.indexOf("\"browser_download_url\"");
                    if (browserDownloadUrlIndex != -1) {
                        int startIndex = jsonResponse.indexOf(":", browserDownloadUrlIndex) + 2;
                        int endIndex = jsonResponse.indexOf("\"", startIndex + 1);
                        downloadUrl = jsonResponse.substring(startIndex, endIndex);
                    } else {
                        downloadUrl = "https://github.com/" + GITHUB_REPO + "/releases/latest";
                        plugin.getLogger().info("Không tìm thấy URL tải xuống trực tiếp, sử dụng trang phát hành GitHub.");
                    }
                } catch (java.net.SocketTimeoutException e) {
                    plugin.getLogger().log(Level.WARNING, "Timeout khi kiểm tra cập nhật từ GitHub. Vui lòng thử lại sau.", e);
                    return;
                } catch (java.net.UnknownHostException e) {
                    plugin.getLogger().log(Level.WARNING, "Không thể kết nối đến GitHub. Vui lòng kiểm tra kết nối mạng của bạn.", e);
                    return;
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Không thể kiểm tra cập nhật từ GitHub: " + ex.getMessage(), ex);
                    return;
                }

                if (githubVersion == null || githubVersion.isEmpty()) {
                    plugin.getLogger().warning("Không thể xác định phiên bản từ GitHub.");
                    return;
                }

                updateAvailable = githubIsNewer();
                devBuildVersion = devBuildIsNewer();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (devBuildVersion) {
                        plugin.getLogger().warning("Bạn đang sử dụng phiên bản DevBuild của KhoKhoangSan Plugin");
                        plugin.getLogger().warning("Hầu hết các tính năng trong DevBuild đã sửa lỗi và có tính năng mới cho phiên bản tiếp theo, và có thể bao gồm các vấn đề khác");
                        plugin.getLogger().warning("Vì vậy, nếu bạn gặp bất kỳ vấn đề nào, vui lòng vào Discord của tôi và báo cáo cho Danh!");
                    }
                    if (updateAvailable) {
                        plugin.getLogger().warning("Một bản cập nhật cho KhoKhoangSan (v" + getGithubVersion() + ") đã có sẵn tại:");
                        plugin.getLogger().warning("https://github.com/" + GITHUB_REPO + "/releases/latest");
                        plugin.getLogger().warning("Bạn đang sử dụng phiên bản v" + pluginVersion);
                        plugin.getLogger().warning("Nếu phiên bản plugin của bạn cao hơn phiên bản GitHub, bạn có thể bỏ qua thông báo này");
                        
                        if (changelog != null && !changelog.isEmpty()) {
                            plugin.getLogger().info("=== CHANGELOG ===");
                            for (String line : changelog.split("\n")) {
                                plugin.getLogger().info(line);
                            }
                            plugin.getLogger().info("================");
                        }
                        
                        Bukkit.getPluginManager().registerEvents(this, plugin);
                    } else {
                        plugin.getLogger().info("Đây là phiên bản mới nhất của KhoKhoangSan Plugin");
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
        try {
            if (version.endsWith("-SNAPSHOT")) {
                version = version.split("-SNAPSHOT")[0];
            }
            return Arrays.stream(version.split("\\.")).mapToInt(Integer::parseInt).toArray();
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Không thể phân tích phiên bản: " + version);
            return null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(@NotNull PlayerJoinEvent e) {
        if (updateAvailable) {
            Player player = e.getPlayer();
            if (player.hasPermission("storage.admin")) {
                player.sendMessage(ChatColor.GREEN + String.format("Đã có bản cập nhật cho KhoKhoangSan tại %s", "https://github.com/" + GITHUB_REPO + "/releases/latest"));
                player.sendMessage(ChatColor.GREEN + String.format("Bạn đang sử dụng phiên bản %s, phiên bản mới là %s", pluginVersion, githubVersion));
                player.sendMessage(ChatColor.GREEN + "Nếu phiên bản plugin của bạn cao hơn phiên bản GitHub, bạn có thể bỏ qua thông báo này");
                
                if (changelog != null && !changelog.isEmpty()) {
                    player.sendMessage(ChatColor.GOLD + "=== THAY ĐỔI ===");
                    for (String line : changelog.split("\n")) {
                        if (!line.trim().isEmpty()) {
                            player.sendMessage(ChatColor.YELLOW + line);
                        }
                    }
                    player.sendMessage(ChatColor.GOLD + "===============");
                }
                
                if (downloadUrl != null && !downloadUrl.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "Bạn có muốn tải xuống phiên bản mới nhất không?");
                    player.sendMessage(ChatColor.GREEN + "Gõ " + ChatColor.WHITE + "/kho update" + ChatColor.GREEN + " để tải xuống bản cập nhật");
                    
                    pendingDownloads.put(player.getUniqueId(), true);
                }
            }
        }
    }
    
    public boolean hasPendingDownload(UUID playerUUID) {
        return pendingDownloads.getOrDefault(playerUUID, false);
    }
    
    public void removePendingDownload(UUID playerUUID) {
        pendingDownloads.remove(playerUUID);
    }
    
    public void downloadUpdate(Player player) {
        if (!updateAvailable || downloadUrl == null || downloadUrl.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Không có bản cập nhật nào để tải xuống.");
            return;
        }
        
        if (!player.hasPermission("storage.admin")) {
            player.sendMessage(ChatColor.RED + "Bạn không có quyền cập nhật plugin.");
            return;
        }
        
        player.sendMessage(ChatColor.YELLOW + "Đang tải xuống phiên bản mới nhất của KhoKhoangSan...");
        player.sendMessage(ChatColor.GRAY + "Phiên bản mới sẽ được cài đặt khi máy chủ khởi động lại.");
        
        new BukkitRunnable() {
            @Override
            public void run() {
                HttpsURLConnection connection = null;
                try {
                    connection = (HttpsURLConnection) new URL(downloadUrl).openConnection();
                    connection.setRequestProperty("Accept", "application/octet-stream");
                    connection.setConnectTimeout(30000);
                    connection.setReadTimeout(30000);
                    
                    String fileName;
                    String disposition = connection.getHeaderField("Content-Disposition");
                    if (disposition != null && disposition.contains("filename=")) {
                        fileName = disposition.substring(disposition.indexOf("filename=") + 9).replace("\"", "");
                    } else {
                        fileName = "KhoKhoangSan-" + githubVersion + ".jar";
                    }
                    
                    Path pluginsDir = Bukkit.getUpdateFolderFile().toPath().getParent();
                    if (!Files.exists(pluginsDir)) {
                        Files.createDirectories(pluginsDir);
                    }
                    
                    Path updateFolder = Paths.get(pluginsDir.toString(), "update");
                    if (!Files.exists(updateFolder)) {
                        Files.createDirectories(updateFolder);
                    }
                    
                    Path outputPath = Paths.get(updateFolder.toString(), fileName);
                    
                    long fileSize = connection.getContentLengthLong();
                    long downloaded = 0;
                    int reportedProgress = 0;
                    final int bufferSize = 8192;
                    
                    try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                         FileOutputStream out = new FileOutputStream(outputPath.toFile())) {
                        byte[] buffer = new byte[bufferSize];
                        int bytesRead;
                        long startTime = System.currentTimeMillis();
                        
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                            downloaded += bytesRead;
                            
                            if (fileSize > 0) {
                                int progress = (int) (downloaded * 100 / fileSize);
                                if (progress >= reportedProgress + 25) {
                                    reportedProgress = progress;
                                    final int currentProgress = progress;
                                    
                                    new BukkitRunnable() {
                                        @Override
                                        public void run() {
                                            player.sendMessage(ChatColor.YELLOW + "Đang tải xuống: " + 
                                                               currentProgress + "% hoàn thành...");
                                        }
                                    }.runTask(plugin);
                                }
                            }
                        }
                        
                        long endTime = System.currentTimeMillis();
                        final double timeInSeconds = (endTime - startTime) / 1000.0;
                        
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                player.sendMessage(ChatColor.GREEN + "Tải xuống hoàn tất trong " + 
                                                  String.format("%.1f", timeInSeconds) + " giây! Đang cài đặt bản cập nhật...");
                                
                                File currentPluginFile = null;
                                for (File file : pluginsDir.toFile().listFiles()) {
                                    if (file.getName().startsWith("KhoKhoangSan") && file.getName().endsWith(".jar")) {
                                        currentPluginFile = file;
                                        break;
                                    }
                                }
                                
                                if (currentPluginFile != null) {
                                    try {
                                        File backupFile = new File(pluginsDir.toFile(), currentPluginFile.getName() + ".bak");
                                        if (backupFile.exists()) {
                                            backupFile.delete();
                                        }
                                        Files.copy(currentPluginFile.toPath(), backupFile.toPath());
                                        
                                        Files.copy(outputPath, currentPluginFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                        
                                        player.sendMessage(ChatColor.GREEN + "Cài đặt bản cập nhật thành công!");
                                        player.sendMessage(ChatColor.YELLOW + "Vui lòng khởi động lại máy chủ để áp dụng bản cập nhật.");
                                        player.sendMessage(ChatColor.GRAY + "Đã tạo bản sao lưu tại: " + backupFile.getName());
                                        
                                        Files.delete(outputPath);
                                        
                                        removePendingDownload(player.getUniqueId());
                                    } catch (IOException e) {
                                        player.sendMessage(ChatColor.RED + "Không thể cài đặt bản cập nhật: " + e.getMessage());
                                        player.sendMessage(ChatColor.RED + "Vui lòng thử tải xuống thủ công tại: https://github.com/" + GITHUB_REPO + "/releases/latest");
                                        plugin.getLogger().log(Level.SEVERE, "Không thể cài đặt bản cập nhật", e);
                                    }
                                } else {
                                    player.sendMessage(ChatColor.RED + "Không tìm thấy file plugin hiện tại!");
                                    player.sendMessage(ChatColor.YELLOW + "Đã tải xuống tại: " + outputPath.toString());
                                    plugin.getLogger().warning("Không tìm thấy file plugin hiện tại! Đã tải xuống tại: " + outputPath.toString());
                                }
                            }
                        }.runTask(plugin);
                        
                    }
                } catch (java.net.SocketTimeoutException e) {
                    final String errorMsg = "Timeout khi kết nối đến server tải xuống. Vui lòng thử lại sau.";
                    showErrorMessage(player, errorMsg, e);
                } catch (java.net.UnknownHostException e) {
                    final String errorMsg = "Không thể kết nối đến server tải xuống. Vui lòng kiểm tra kết nối mạng của bạn.";
                    showErrorMessage(player, errorMsg, e);
                } catch (Exception e) {
                    final String errorMsg = "Không thể tải xuống bản cập nhật: " + e.getMessage();
                    showErrorMessage(player, errorMsg, e);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
            
            private void showErrorMessage(Player player, String message, Exception e) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendMessage(ChatColor.RED + message);
                        player.sendMessage(ChatColor.YELLOW + "Vui lòng tải xuống thủ công tại: " + 
                                          ChatColor.WHITE + "https://github.com/" + GITHUB_REPO + "/releases/latest");
                        plugin.getLogger().log(Level.SEVERE, message, e);
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }
}