package com.hongminh54.storage.Database;

import java.util.Date;

/**
 * Lưu trữ thông tin lịch sử giao dịch chuyển tài nguyên
 */
public class TransferHistory {
    private final int id;
    private final String senderName;
    private final String receiverName;
    private final String material;
    private final int amount;
    private final long timestamp;

    /**
     * Khởi tạo đối tượng lịch sử giao dịch
     * @param id ID giao dịch
     * @param senderName Tên người gửi
     * @param receiverName Tên người nhận
     * @param material Loại tài nguyên
     * @param amount Số lượng
     * @param timestamp Thời gian giao dịch
     */
    public TransferHistory(int id, String senderName, String receiverName, String material, int amount, long timestamp) {
        this.id = id;
        this.senderName = senderName;
        this.receiverName = receiverName;
        this.material = material;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    /**
     * Khởi tạo đối tượng lịch sử giao dịch với thời gian hiện tại
     * @param id ID giao dịch
     * @param senderName Tên người gửi
     * @param receiverName Tên người nhận
     * @param material Loại tài nguyên
     * @param amount Số lượng
     */
    public TransferHistory(int id, String senderName, String receiverName, String material, int amount) {
        this(id, senderName, receiverName, material, amount, System.currentTimeMillis());
    }

    /**
     * Lấy ID giao dịch
     * @return ID giao dịch
     */
    public int getId() {
        return id;
    }

    /**
     * Lấy tên người gửi
     * @return Tên người gửi
     */
    public String getSenderName() {
        return senderName;
    }

    /**
     * Lấy tên người nhận
     * @return Tên người nhận
     */
    public String getReceiverName() {
        return receiverName;
    }

    /**
     * Lấy loại tài nguyên
     * @return Loại tài nguyên
     */
    public String getMaterial() {
        return material;
    }

    /**
     * Lấy số lượng
     * @return Số lượng
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Lấy thời gian giao dịch
     * @return Thời gian giao dịch
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Lấy thời gian giao dịch dưới dạng Date
     * @return Thời gian giao dịch
     */
    public Date getDate() {
        return new Date(timestamp);
    }

    /**
     * Chuyển đối tượng thành chuỗi thông tin
     * @return Chuỗi thông tin
     */
    @Override
    public String toString() {
        return "TransferHistory{" +
                "id=" + id +
                ", senderName='" + senderName + '\'' +
                ", receiverName='" + receiverName + '\'' +
                ", material='" + material + '\'' +
                ", amount=" + amount +
                ", timestamp=" + new Date(timestamp) +
                '}';
    }
} 