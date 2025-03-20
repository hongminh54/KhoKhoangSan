package com.hongminh54.storage.Utils;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Lớp tiện ích để xử lý các hoạt động liên quan đến số học và định dạng số
 * trong plugin Kho Khoáng Sản.
 * <p>
 * Cung cấp các phương thức để:
 * <ul>
 *     <li>Định dạng số với hậu tố (K, M, B...)</li>
 *     <li>Tạo số ngẫu nhiên trong phạm vi</li>
 *     <li>Chuyển đổi chuỗi thành số với xử lý lỗi</li>
 * </ul>
 */
public class Number {

    /** Mảng hậu tố được sử dụng cho việc định dạng số lớn */
    private static final String[] SUFFIXES = new String[]{"", "K", "M", "B", "T", "Q"};
    
    /** Định dạng số được sử dụng mặc định trong plugin */
    private static final DecimalFormat DEFAULT_FORMAT = new DecimalFormat("#,##0.##");

    /**
     * Định dạng số nguyên thành chuỗi ngắn gọn với hậu tố (K, M, B...)
     * 
     * @param number Số cần định dạng
     * @return Chuỗi đã định dạng (ví dụ: 1500 -> 1.5K)
     */
    public static String format(int number) {
        if (number < 1000) {
            return String.valueOf(number);
        }
        
        String r = new DecimalFormat("##0E0").format(number);
        r = r.replaceAll("E[0-9]", SUFFIXES[Character.getNumericValue(r.charAt(r.length() - 1)) / 3]);
        int MAX_LENGTH = 4;
        while (r.length() > MAX_LENGTH || r.matches("[0-9]+\\.[a-z]")) {
            r = r.substring(0, r.length() - 2) + r.substring(r.length() - 1);
        }
        return r;
    }

    /**
     * Định dạng số theo cài đặt trong config
     * 
     * @param number Số cần định dạng
     * @return Chuỗi đã định dạng
     */
    public static String settingFormat(int number) {
        String configFormat = File.getConfig().getString("number_format", "#.##");
        try {
            DecimalFormat df = new DecimalFormat(configFormat);
            return df.format(number);
        } catch (IllegalArgumentException e) {
            return format(number);
        }
    }

    /**
     * Lấy số thực ngẫu nhiên trong khoảng [min, max)
     * 
     * @param min Giá trị tối thiểu (bao gồm)
     * @param max Giá trị tối đa (không bao gồm)
     * @return Số thực ngẫu nhiên trong khoảng
     */
    public static double getRandomDouble(double min, double max) {
        if (max > min) {
            return ThreadLocalRandom.current().nextDouble(min, max);
        } else {
            return min;
        }
    }

    /**
     * Lấy số nguyên ngẫu nhiên trong khoảng [min, max)
     * 
     * @param min Giá trị tối thiểu (bao gồm)
     * @param max Giá trị tối đa (không bao gồm)
     * @return Số nguyên ngẫu nhiên trong khoảng
     */
    public static int getRandomInteger(int min, int max) {
        if (max >= min + 2) {
            return ThreadLocalRandom.current().nextInt(min, max);
        } else {
            return min;
        }
    }

    /**
     * Lấy số nguyên dài (long) ngẫu nhiên trong khoảng [min, max)
     * 
     * @param min Giá trị tối thiểu (bao gồm)
     * @param max Giá trị tối đa (không bao gồm)
     * @return Số nguyên dài ngẫu nhiên trong khoảng
     */
    public static long getRandomLong(long min, long max) {
        if (max >= min + 2) {
            return ThreadLocalRandom.current().nextLong(min, max);
        } else {
            return min;
        }
    }

    /**
     * Chuyển đổi chuỗi thành số thực (double)
     * <p>
     * Hỗ trợ định dạng phạm vi "min-max" để trả về số ngẫu nhiên trong khoảng
     * 
     * @param s Chuỗi cần chuyển đổi
     * @return Số thực, hoặc 0 nếu không thể chuyển đổi
     */
    public static double getDouble(String s) {
        if (s == null || s.isEmpty()) {
            return 0d;
        }
        
        try {
            if (!s.contains("-")) {
                return BigDecimal.valueOf(Double.parseDouble(s)).doubleValue();
            } else {
                String[] parts = s.split("-");
                if (parts.length != 2) {
                    return 0d;
                }
                return getRandomDouble(
                    BigDecimal.valueOf(Double.parseDouble(parts[0])).doubleValue(),
                    BigDecimal.valueOf(Double.parseDouble(parts[1])).doubleValue()
                );
            }
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    /**
     * Chuyển đổi chuỗi thành số nguyên (int)
     * <p>
     * Hỗ trợ định dạng phạm vi "min-max" để trả về số ngẫu nhiên trong khoảng
     * 
     * @param s Chuỗi cần chuyển đổi
     * @return Số nguyên, hoặc 0 nếu không thể chuyển đổi
     */
    public static int getInteger(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        
        try {
            if (!s.contains("-")) {
                return Integer.parseInt(s);
            } else {
                String[] parts = s.split("-");
                if (parts.length != 2) {
                    return 0;
                }
                return getRandomInteger(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1])
                );
            }
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Chuyển đổi chuỗi thành số nguyên dài (long)
     * <p>
     * Hỗ trợ định dạng phạm vi "min-max" để trả về số ngẫu nhiên trong khoảng
     * 
     * @param s Chuỗi cần chuyển đổi
     * @return Số nguyên dài, hoặc 0 nếu không thể chuyển đổi
     */
    public static long getLong(String s) {
        if (s == null || s.isEmpty() || s.equalsIgnoreCase("all")) {
            return 0L;
        }
        
        try {
            if (!s.contains("-")) {
                return Long.parseLong(s);
            } else {
                String[] parts = s.split("-");
                if (parts.length != 2) {
                    return 0L;
                }
                return getRandomLong(
                    Long.parseLong(parts[0]),
                    Long.parseLong(parts[1])
                );
            }
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Định dạng số nguyên với hậu tố (K, M, B...)
     * 
     * @param number Số cần định dạng
     * @return Chuỗi đã định dạng (ví dụ: 1500 -> 1.50k)
     */
    public static String intToSuffixedNumber(int number) {
        if (number < 1000) {
            return String.valueOf(number);
        } else {
            int exponent = (int) (Math.log(number) / Math.log(1000));
            return String.format("%.2f%c", number / Math.pow(1000, exponent), "kMBTQ".charAt(exponent - 1));
        }
    }

    /**
     * Định dạng số nguyên dài với hậu tố (K, M, B...)
     * 
     * @param number Số cần định dạng
     * @return Chuỗi đã định dạng
     */
    public static String longToSuffixedNumber(long number) {
        if (number < 1000) {
            return String.valueOf(number);
        } else {
            int exponent = (int) (Math.log(number) / Math.log(1000));
            return String.format("%.2f%c", number / Math.pow(1000, exponent), "kMBTQ".charAt(exponent - 1));
        }
    }

    /**
     * Định dạng số thực với hậu tố (K, M, B...)
     * 
     * @param number Số cần định dạng
     * @return Chuỗi đã định dạng
     */
    public static String doubleToSuffixedNumber(double number) {
        if (number < 1000) {
            return String.format("%.2f", number);
        } else {
            int exponent = (int) (Math.log(number) / Math.log(1000));
            return String.format("%.2f%c", number / Math.pow(1000, exponent), "kMBTQ".charAt(exponent - 1));
        }
    }

    /**
     * Định dạng số thành chuỗi với dấu phân cách hàng nghìn
     * 
     * @param number Số cần định dạng
     * @return Chuỗi đã định dạng với dấu phân cách
     */
    public static String formatWithSeparator(int number) {
        return DEFAULT_FORMAT.format(number);
    }

    /**
     * Định dạng số thành chuỗi với dấu phân cách hàng nghìn
     * 
     * @param number Số cần định dạng
     * @return Chuỗi đã định dạng với dấu phân cách
     */
    public static String formatWithSeparator(long number) {
        return DEFAULT_FORMAT.format(number);
    }

    /**
     * Định dạng số thành chuỗi với dấu phân cách hàng nghìn
     * 
     * @param number Số cần định dạng
     * @return Chuỗi đã định dạng với dấu phân cách
     */
    public static String formatWithSeparator(double number) {
        return DEFAULT_FORMAT.format(number);
    }

    /**
     * Định dạng số với format ngắn gọn (K, M, B, T)
     * Ví dụ: 1000 -> 1K, 1000000 -> 1M, 1500000 -> 1.5M
     * 
     * @param number Số cần định dạng
     * @return Chuỗi đã định dạng
     */
    public static String formatCompact(long number) {
        if (number < 1000) {
            return String.valueOf(number);
        }
        
        int exp = (int) (Math.log10(number) / 3);
        double value = number / Math.pow(1000, exp);
        String format = value % 1 == 0 ? "%.0f%s" : "%.1f%s";
        return String.format(format, value, SUFFIXES[exp]);
    }
    
    /**
     * Định dạng số với format ngắn gọn (K, M, B, T)
     * Ví dụ: 1000 -> 1K, 1000000 -> 1M, 1500000 -> 1.5M
     * 
     * @param number Số cần định dạng
     * @return Chuỗi đã định dạng
     */
    public static String formatCompact(int number) {
        return formatCompact((long) number);
    }
}
