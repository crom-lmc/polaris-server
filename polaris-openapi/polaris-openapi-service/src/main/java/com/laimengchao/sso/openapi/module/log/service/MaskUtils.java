package com.laimengchao.sso.openapi.module.log.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.laimengchao.sso.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 报文脱敏：手机号 / 身份证 / 邮箱 / 令牌 正则替换，超长截断。落库前统一走这里。
 * <p>
 * 脱敏规则由平台设置 {@code mask.rules}（JSON）动态覆盖，启动时加载、保存时刷新；
 * 未配置或配置非法时使用默认规则（与历史硬编码行为一致：手机号前3后4、身份证前6后4）。
 */
@Slf4j
public final class MaskUtils {

    /** 当前生效的脱敏规则（默认与历史行为一致） */
    private static volatile MaskConfig config = MaskConfig.defaults();

    /* 定位正则：只捕获需脱敏的整体，前缀/后缀保留位数由 config 决定 */
    private static final Pattern MOBILE = Pattern.compile("(?<![0-9])(1[3-9][0-9]{9})(?![0-9])");
    private static final Pattern ID_CARD = Pattern.compile("(?<![0-9])([0-9]{17}[0-9Xx])(?![0-9])");
    private static final Pattern EMAIL = Pattern.compile("([\\w.+-]+)@([\\w.-]+)");
    private static final Pattern BEARER = Pattern.compile("(Bearer\\s+)[A-Za-z0-9._-]{8,}");

    private MaskUtils() {
    }

    /** 由平台设置加载/刷新规则；JSON 非法时保留当前规则并留痕，绝不影响调用链路。 */
    public static void configure(String json) {
        if (json == null || json.isBlank()) {
            config = MaskConfig.defaults();
            return;
        }
        try {
            Map<String, Object> m = JsonUtils.fromJson(json, new TypeReference<Map<String, Object>>() {
            });
            config = MaskConfig.from(m == null ? Map.of() : m);
            log.info("脱敏规则已刷新：{}", json);
        } catch (Exception e) {
            log.warn("脱敏规则 mask.rules 解析失败，保留原规则：{}", e.getMessage());
        }
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        MaskConfig c = config;
        String s = text;
        if (c.mobileEnabled) {
            s = maskDigits(s, MOBILE, c.mobilePrefix, c.mobileSuffix);
        }
        if (c.idcardEnabled) {
            s = maskDigits(s, ID_CARD, c.idcardPrefix, c.idcardSuffix);
        }
        if (c.emailEnabled) {
            s = EMAIL.matcher(s).replaceAll("$1@***");
        }
        if (c.tokenEnabled) {
            s = BEARER.matcher(s).replaceAll("$1***");
        }
        if (s.length() > c.maxLen) {
            s = s.substring(0, c.maxLen) + "...(truncated)";
        }
        return s;
    }

    /** 对捕获的整段数字按 prefix/suffix 打星，中间用等长星号填充。 */
    private static String maskDigits(String s, Pattern pattern, int prefix, int suffix) {
        Matcher m = pattern.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String v = m.group(1);
            int pre = Math.max(0, Math.min(prefix, v.length()));
            int suf = Math.max(0, Math.min(suffix, v.length() - pre));
            int stars = v.length() - pre - suf;
            String masked = v.substring(0, pre) + "*".repeat(Math.max(0, stars))
                    + v.substring(v.length() - suf);
            m.appendReplacement(sb, Matcher.quoteReplacement(masked));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 可配置脱敏规则：每类开关、数字保留位数、最大长度。 */
    private static final class MaskConfig {
        boolean mobileEnabled = true;
        int mobilePrefix = 3;
        int mobileSuffix = 4;
        boolean idcardEnabled = true;
        int idcardPrefix = 6;
        int idcardSuffix = 4;
        boolean emailEnabled = true;
        boolean tokenEnabled = true;
        int maxLen = 4000;

        static MaskConfig defaults() {
            return new MaskConfig();
        }

        static MaskConfig from(Map<String, Object> m) {
            MaskConfig c = new MaskConfig();
            c.mobileEnabled = bool(m, "mobileEnabled", true);
            c.mobilePrefix = intOf(m, "mobilePrefix", 3);
            c.mobileSuffix = intOf(m, "mobileSuffix", 4);
            c.idcardEnabled = bool(m, "idcardEnabled", true);
            c.idcardPrefix = intOf(m, "idcardPrefix", 6);
            c.idcardSuffix = intOf(m, "idcardSuffix", 4);
            c.emailEnabled = bool(m, "emailEnabled", true);
            c.tokenEnabled = bool(m, "tokenEnabled", true);
            c.maxLen = intOf(m, "maxLen", 4000);
            return c;
        }

        private static boolean bool(Map<String, Object> m, String k, boolean def) {
            Object v = m.get(k);
            return v == null ? def : Boolean.parseBoolean(String.valueOf(v));
        }

        private static int intOf(Map<String, Object> m, String k, int def) {
            Object v = m.get(k);
            if (v == null) {
                return def;
            }
            try {
                return Integer.parseInt(String.valueOf(v));
            } catch (NumberFormatException e) {
                return def;
            }
        }
    }
}
