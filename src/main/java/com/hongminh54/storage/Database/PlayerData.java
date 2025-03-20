package com.hongminh54.storage.Database;

public class PlayerData {
    private final String player;
    private final String data;
    private final int max;
    private String statsData; // Dữ liệu thống kê của người chơi

    public PlayerData(String player, String data, int max) {
        this.player = player;
        this.data = data;
        this.max = max;
        this.statsData = "{}"; // Giá trị mặc định
    }
    
    public PlayerData(String player, String data, int max, String statsData) {
        this.player = player;
        this.data = data;
        this.max = max;
        this.statsData = statsData;
    }

    public String getPlayer() {
        return player;
    }

    public String getData() {
        return data;
    }

    public int getMax() {
        return max;
    }
    
    public String getStatsData() {
        return statsData;
    }
    
    public void setStatsData(String statsData) {
        this.statsData = statsData;
    }
}
