package com.hongminh54.storage.Utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.hongminh54.storage.Storage;

/**
 * Tiện ích nén và giải nén dữ liệu để giảm kích thước cơ sở dữ liệu
 */
public class CompressUtils {
    
    // Kích thước tối thiểu dữ liệu để nén (byte)
    private static final int MIN_SIZE_TO_COMPRESS = 100;
    
    // Tiền tố cho dữ liệu đã nén
    private static final String COMPRESSED_PREFIX = "COMPRESSED:";
    
    /**
     * Nén chuỗi sử dụng GZIP và mã hóa Base64
     * @param data Chuỗi cần nén
     * @return Chuỗi đã nén, hoặc chuỗi gốc nếu nén thất bại hoặc kích thước nhỏ
     */
    public static String compressString(String data) {
        // Kiểm tra null hoặc chuỗi rỗng
        if (data == null || data.isEmpty()) {
            return data;
        }
        
        // Kiểm tra kích thước tối thiểu để nén
        if (data.length() < MIN_SIZE_TO_COMPRESS) {
            return data;
        }
        
        // Kiểm tra nếu dữ liệu đã được nén
        if (data.startsWith(COMPRESSED_PREFIX)) {
            return data;
        }
        
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(bos);
            gzip.write(data.getBytes(StandardCharsets.UTF_8));
            gzip.close();
            
            String compressed = Base64.getEncoder().encodeToString(bos.toByteArray());
            
            // Chỉ sử dụng chuỗi nén nếu nó nhỏ hơn chuỗi gốc
            if (compressed.length() < data.length()) {
                return COMPRESSED_PREFIX + compressed;
            } else {
                return data; // Giữ nguyên chuỗi gốc nếu không giảm kích thước
            }
        } catch (IOException e) {
            Storage.getStorage().getLogger().log(Level.WARNING, "Lỗi khi nén dữ liệu: " + e.getMessage());
            return data; // Trả về chuỗi gốc nếu nén thất bại
        }
    }
    
    /**
     * Giải nén chuỗi đã được nén bằng GZIP và mã hóa Base64
     * @param data Chuỗi đã nén
     * @return Chuỗi gốc sau khi giải nén, hoặc chuỗi đầu vào nếu nó không được nén
     */
    public static String decompressString(String data) {
        // Kiểm tra null hoặc chuỗi rỗng
        if (data == null || data.isEmpty()) {
            return data;
        }
        
        // Kiểm tra xem dữ liệu có được nén không
        if (!data.startsWith(COMPRESSED_PREFIX)) {
            return data;
        }
        
        try {
            // Cắt bỏ tiền tố để lấy dữ liệu nén thực tế
            String compressedData = data.substring(COMPRESSED_PREFIX.length());
            
            byte[] decoded = Base64.getDecoder().decode(compressedData);
            GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(decoded));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            gis.close();
            
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Storage.getStorage().getLogger().log(Level.WARNING, "Lỗi khi giải nén dữ liệu: " + e.getMessage());
            return data; // Trả về chuỗi đầu vào nếu giải nén thất bại
        }
    }
    
    /**
     * Kiểm tra xem chuỗi có được nén không
     * @param data Chuỗi cần kiểm tra
     * @return true nếu chuỗi đã được nén, false nếu không
     */
    public static boolean isCompressed(String data) {
        return data != null && data.startsWith(COMPRESSED_PREFIX);
    }
} 