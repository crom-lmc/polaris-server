package com.laimengchao.sso.ehr.common;

/**
 * 敏感字段掩码：手机号保留前 3 后 4，身份证保留前 6 后 4，银行卡仅保留后 4。
 * 无敏感权限时所有列表/详情统一走这里，有权限的明文接口单独放行。
 */
public final class SensitiveMasker {

    private SensitiveMasker() {
    }

    public static String maskMobile(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String v = value.trim();
        if (v.length() < 8) {
            return "****";
        }
        return v.substring(0, 3) + stars(v.length() - 7) + v.substring(v.length() - 4);
    }

    public static String maskIdCard(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String v = value.trim();
        if (v.length() < 11) {
            return "****";
        }
        return v.substring(0, 6) + stars(v.length() - 10) + v.substring(v.length() - 4);
    }

    public static String maskBankAccount(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String v = value.trim();
        if (v.length() < 5) {
            return "****";
        }
        return "****" + v.substring(v.length() - 4);
    }

    private static String stars(int count) {
        return "*".repeat(Math.max(count, 0));
    }
}
