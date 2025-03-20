package com.hongminh54.storage.Database;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Lớp chứa các thông điệp lỗi chuẩn hóa liên quan đến cơ sở dữ liệu.
 * <p>
 * Cung cấp các hằng số và phương thức để lấy thông điệp lỗi cho các tình huống
 * phổ biến khi làm việc với cơ sở dữ liệu.
 */
public class Errors {
    
    /** Thông điệp khi không thể thực thi câu lệnh SQL */
    private static final String SQL_EXECUTION_ERROR = "Couldn't execute SQL statement: ";
    
    /** Thông điệp khi không thể đóng kết nối SQL */
    private static final String SQL_CLOSE_ERROR = "Failed to close SQL connection: ";
    
    /** Thông điệp khi không thể thiết lập kết nối SQL */
    private static final String SQL_CONNECTION_ERROR = "Unable to retrieve SQL connection: ";
    
    /** Thông điệp khi không tìm thấy bảng dữ liệu */
    private static final String TABLE_NOT_FOUND = "Database Error: No Table Found";
    
    /** Thông điệp khi xảy ra lỗi trong quá trình tạo bảng */
    private static final String TABLE_CREATION_ERROR = "Database Error: Failed to create table";
    
    /** Thông điệp khi dữ liệu không hợp lệ */
    private static final String INVALID_DATA = "Database Error: Invalid data format";
    
    /** Thông điệp khi xảy ra lỗi trong quá trình chèn dữ liệu */
    private static final String INSERT_ERROR = "Database Error: Failed to insert data";
    
    /** Thông điệp khi xảy ra lỗi trong quá trình cập nhật dữ liệu */
    private static final String UPDATE_ERROR = "Database Error: Failed to update data";
    
    /** Thông điệp khi xảy ra lỗi trong quá trình xóa dữ liệu */
    private static final String DELETE_ERROR = "Database Error: Failed to delete data";

    /**
     * Lấy thông điệp lỗi khi không thể thực thi câu lệnh SQL
     * 
     * @return Thông điệp lỗi
     */
    @Contract(pure = true)
    public static @NotNull String sqlConnectionExecute() {
        return SQL_EXECUTION_ERROR;
    }

    /**
     * Lấy thông điệp lỗi khi không thể đóng kết nối SQL
     * 
     * @return Thông điệp lỗi
     */
    @Contract(pure = true)
    public static @NotNull String sqlConnectionClose() {
        return SQL_CLOSE_ERROR;
    }

    /**
     * Lấy thông điệp lỗi khi không thể thiết lập kết nối SQL
     * 
     * @return Thông điệp lỗi
     */
    @Contract(pure = true)
    public static @NotNull String noSQLConnection() {
        return SQL_CONNECTION_ERROR;
    }

    /**
     * Lấy thông điệp lỗi khi không tìm thấy bảng dữ liệu
     * 
     * @return Thông điệp lỗi
     */
    @Contract(pure = true)
    public static @NotNull String noTableFound() {
        return TABLE_NOT_FOUND;
    }
    
    /**
     * Lấy thông điệp lỗi khi xảy ra lỗi trong quá trình tạo bảng
     * 
     * @return Thông điệp lỗi
     */
    @Contract(pure = true)
    public static @NotNull String tableCreationError() {
        return TABLE_CREATION_ERROR;
    }
    
    /**
     * Lấy thông điệp lỗi khi dữ liệu không hợp lệ
     * 
     * @return Thông điệp lỗi
     */
    @Contract(pure = true)
    public static @NotNull String invalidData() {
        return INVALID_DATA;
    }
    
    /**
     * Lấy thông điệp lỗi khi xảy ra lỗi trong quá trình chèn dữ liệu
     * 
     * @return Thông điệp lỗi
     */
    @Contract(pure = true)
    public static @NotNull String insertError() {
        return INSERT_ERROR;
    }
    
    /**
     * Lấy thông điệp lỗi khi xảy ra lỗi trong quá trình cập nhật dữ liệu
     * 
     * @return Thông điệp lỗi
     */
    @Contract(pure = true)
    public static @NotNull String updateError() {
        return UPDATE_ERROR;
    }
    
    /**
     * Lấy thông điệp lỗi khi xảy ra lỗi trong quá trình xóa dữ liệu
     * 
     * @return Thông điệp lỗi
     */
    @Contract(pure = true)
    public static @NotNull String deleteError() {
        return DELETE_ERROR;
    }
}
