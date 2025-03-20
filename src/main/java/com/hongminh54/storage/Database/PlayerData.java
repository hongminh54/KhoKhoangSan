package com.hongminh54.storage.Database;

/**
 * Lớp đại diện cho dữ liệu kho của người chơi trong plugin Kho Khoáng Sản.
 * <p>
 * Lưu trữ thông tin về các vật phẩm, giới hạn số lượng và thống kê của người chơi.
 */
public class PlayerData {
    /** Tên người chơi */
    private final String player;
    
    /** Dữ liệu về vật phẩm trong kho (định dạng JSON) */
    private final String data;
    
    /** Số lượng vật phẩm tối đa có thể lưu trữ */
    private final int max;
    
    /** Dữ liệu thống kê của người chơi (định dạng JSON) */
    private String statsData;
    
    /** Định dạng mặc định cho dữ liệu thống kê rỗng */
    private static final String DEFAULT_STATS = "{}";

    /**
     * Khởi tạo đối tượng PlayerData với thống kê rỗng
     *
     * @param player Tên người chơi
     * @param data Dữ liệu kho (JSON)
     * @param max Số lượng vật phẩm tối đa
     */
    public PlayerData(String player, String data, int max) {
        this.player = player != null ? player : "";
        this.data = data != null ? data : "{}";
        this.max = max > 0 ? max : 1000; // Giá trị mặc định 1000 nếu max ≤ 0
        this.statsData = DEFAULT_STATS;
    }
    
    /**
     * Khởi tạo đối tượng PlayerData với đầy đủ thông tin
     *
     * @param player Tên người chơi
     * @param data Dữ liệu kho (JSON)
     * @param max Số lượng vật phẩm tối đa
     * @param statsData Dữ liệu thống kê (JSON)
     */
    public PlayerData(String player, String data, int max, String statsData) {
        this.player = player != null ? player : "";
        this.data = data != null ? data : "{}";
        this.max = max > 0 ? max : 1000;
        this.statsData = statsData != null ? statsData : DEFAULT_STATS;
    }

    /**
     * Lấy tên người chơi
     *
     * @return Tên người chơi
     */
    public String getPlayer() {
        return player;
    }

    /**
     * Lấy dữ liệu kho của người chơi
     *
     * @return Dữ liệu kho dạng JSON
     */
    public String getData() {
        return data;
    }

    /**
     * Lấy số lượng tối đa mà người chơi có thể lưu trữ
     *
     * @return Số lượng tối đa
     */
    public int getMax() {
        return max;
    }
    
    /**
     * Lấy dữ liệu thống kê của người chơi
     *
     * @return Dữ liệu thống kê dạng JSON
     */
    public String getStatsData() {
        return statsData;
    }
    
    /**
     * Cập nhật dữ liệu thống kê của người chơi
     *
     * @param statsData Dữ liệu thống kê mới
     */
    public void setStatsData(String statsData) {
        this.statsData = statsData != null ? statsData : DEFAULT_STATS;
    }
    
    /**
     * Kiểm tra xem dữ liệu thống kê có rỗng không
     *
     * @return true nếu dữ liệu thống kê rỗng
     */
    public boolean isStatsEmpty() {
        return DEFAULT_STATS.equals(statsData) || statsData.isEmpty();
    }
    
    /**
     * Xóa tất cả dữ liệu thống kê
     */
    public void clearStats() {
        this.statsData = DEFAULT_STATS;
    }
    
    /**
     * Tạo chuỗi biểu diễn của đối tượng cho mục đích gỡ lỗi
     *
     * @return Chuỗi biểu diễn
     */
    @Override
    public String toString() {
        return "PlayerData{" +
                "player='" + player + '\'' +
                ", data='" + (data.length() > 20 ? data.substring(0, 17) + "..." : data) + '\'' +
                ", max=" + max +
                ", stats=" + (statsData.length() > 20 ? statsData.substring(0, 17) + "..." : statsData) +
                '}';
    }
}
