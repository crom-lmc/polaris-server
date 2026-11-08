package com.laimengchao.sso.account.security;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 统一密码强度策略：所有「设置 / 修改密码」的入口（自助改密、管理员重置、新建账户）都走这里，
 * 避免策略分散在多处、前端拦了后端没拦。
 *
 * <p>规则：
 * <ol>
 *   <li>长度 ≥ 8 位；</li>
 *   <li>不允许包含危险特殊字符（shell / HTML / SQL 元字符与空白），避免被插值进命令行、SQL、HTML 时引发注入；</li>
 *   <li>大写字母、小写字母、数字、特殊字符四类，至少满足三类。</li>
 * </ol>
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    /**
     * 危险特殊字符：shell / HTML / SQL 元字符与空白（空格、制表符）。
     * 这些字符一旦被拼进命令行、SQL、HTML 会成为注入源，故在密码中禁用。
     * 注意：字符串里的 {@code \\} 表示一个反斜杠，{@code \t} 表示制表符。
     */
    private static final String DANGEROUS = "<>\"'\\`|;&$(){}[]%~ \t";

    private static final Set<Character> DANGEROUS_SET = DANGEROUS.chars()
            .mapToObj(c -> (char) c)
            .collect(Collectors.toSet());

    private PasswordPolicy() {
    }

    /** 校验不通过直接抛 BizException（与现有 Asserts 风格一致）。 */
    public static void validate(String password) {
        if (password == null || password.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "密码不能为空");
        }
        if (password.length() < MIN_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR, "密码至少 " + MIN_LENGTH + " 位");
        }
        for (char c : password.toCharArray()) {
            if (DANGEROUS_SET.contains(c)) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "密码包含不允许的危险字符：" + describe(c));
            }
        }
        int types = countTypes(password);
        if (types < 3) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "密码需包含大写字母、小写字母、数字、特殊字符中至少 3 类");
        }
    }

    /**
     * 字符类别数：大写、小写、数字、特殊（特殊 = 非字母数字且不在危险集内）。
     * 供前端做一致性校验时复用同一口径。
     */
    public static int countTypes(String password) {
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean special = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                upper = true;
            } else if (Character.isLowerCase(c)) {
                lower = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            } else if (!DANGEROUS_SET.contains(c)) {
                special = true;
            }
        }
        int n = 0;
        if (upper) n++;
        if (lower) n++;
        if (digit) n++;
        if (special) n++;
        return n;
    }

    private static String describe(char c) {
        if (c == ' ') return "空格";
        if (c == '\t') return "制表符";
        return String.valueOf(c);
    }
}
