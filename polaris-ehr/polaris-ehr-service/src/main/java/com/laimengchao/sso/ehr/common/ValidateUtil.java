package com.laimengchao.sso.ehr.common;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;

/**
 * 人员信息格式校验工具。全部纯函数，供 EmployeeService 与子档案服务共用。
 * <p>
 * 身份证按 GB 11643-1999（18 位，ISO 7064 MOD 11-2 校验码）验证；
 * 15 位老身份证不收——存量数据里如有，也只在前端提示补录，不做自动换算。
 */
public final class ValidateUtil {

    private ValidateUtil() {
    }

    private static final int[] ID_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] ID_CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    private static final String MOBILE_REGEX = "^1[3-9]\\d{9}$";
    private static final String EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    /** 18 位身份证号（含 X 小写容忍），校验码 + 生日段 + 省份码三重验证 */
    public static boolean isIdCard(String raw) {
        if (raw == null) {
            return false;
        }
        String id = raw.trim().toUpperCase();
        if (!id.matches("^\\d{17}[0-9X]$")) {
            return false;
        }
        // 省份码（1-2 位）必须存在于行政区划表
        int province = Integer.parseInt(id.substring(0, 2));
        if (province < 11 || province > 82 || !isKnownProvince(province)) {
            return false;
        }
        // 生日段必须是真实日期
        try {
            LocalDate birth = LocalDate.parse(id.substring(6, 10) + "-"
                    + id.substring(10, 12) + "-" + id.substring(12, 14));
            if (birth.isAfter(LocalDate.now())) {
                return false;
            }
        } catch (DateTimeParseException e) {
            return false;
        }
        // 校验码
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (id.charAt(i) - '0') * ID_WEIGHTS[i];
        }
        return ID_CHECK_CODES[sum % 11] == id.charAt(17);
    }

    private static boolean isKnownProvince(int code) {
        // 直辖市 + 各省 + 自治区 + 特别行政区的两位码（不含港澳台证段之外的组合）
        return switch (code) {
            case 11, 12, 13, 14, 15, // 京津冀晋蒙
                 21, 22, 23, // 辽吉黑
                 31, 32, 33, 34, 35, 36, 37, // 沪苏浙皖闽赣鲁
                 41, 42, 43, 44, 45, 46, // 豫鄂湘粤桂琼
                 50, 51, 52, 53, 54, // 渝川黔滇藏
                 61, 62, 63, 64, 65, // 陕甘青宁新
                 71, 81, 82 // 港澳台
                    -> true;
            default -> false;
        };
    }

    public static boolean isMobile(String raw) {
        return raw != null && raw.trim().matches(MOBILE_REGEX);
    }

    public static boolean isEmail(String raw) {
        return raw != null && raw.trim().matches(EMAIL_REGEX);
    }

    /** 从身份证号推导出生日期；非 18 位合法身份证返回 null */
    public static LocalDate birthdayFromIdCard(String raw) {
        if (!isIdCard(raw)) {
            return null;
        }
        String id = raw.trim().toUpperCase();
        return LocalDate.parse(id.substring(6, 10) + "-"
                + id.substring(10, 12) + "-" + id.substring(12, 14));
    }

    /** 从身份证号推导性别（第 17 位奇数男/偶数女）；不合法返回 null */
    public static Integer genderFromIdCard(String raw) {
        if (!isIdCard(raw)) {
            return null;
        }
        String id = raw.trim().toUpperCase();
        return (id.charAt(16) - '0') % 2 == 1 ? 1 : 2;
    }

    /** 年龄（周岁）；生日为空或在未来返回 null */
    public static Integer age(LocalDate birthday) {
        if (birthday == null || birthday.isAfter(LocalDate.now())) {
            return null;
        }
        return Period.between(birthday, LocalDate.now()).getYears();
    }
}
