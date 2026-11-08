package com.laimengchao.sso.util;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;

/**
 * 业务断言。条件不满足直接抛 {@link BizException}，由全局异常处理成统一响应。
 */
public final class Asserts {

    private Asserts() {
    }

    public static void isTrue(boolean expression, ErrorCode errorCode) {
        if (!expression) {
            throw new BizException(errorCode);
        }
    }

    public static void isTrue(boolean expression, ErrorCode errorCode, String detail) {
        if (!expression) {
            throw new BizException(errorCode, detail);
        }
    }

    public static void isFalse(boolean expression, ErrorCode errorCode) {
        isTrue(!expression, errorCode);
    }

    public static void isFalse(boolean expression, ErrorCode errorCode, String detail) {
        isTrue(!expression, errorCode, detail);
    }

    public static void notNull(Object obj, ErrorCode errorCode, String detail) {
        if (obj == null) {
            throw new BizException(errorCode, detail);
        }
    }

    public static void notEmpty(String str, ErrorCode errorCode, String detail) {
        if (str == null || str.isBlank()) {
            throw new BizException(errorCode, detail);
        }
    }
}
