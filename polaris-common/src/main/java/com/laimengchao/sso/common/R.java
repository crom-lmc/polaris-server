package com.laimengchao.sso.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 统一 API 响应体（全服务唯一）：{ code, message, data, timestamp }。
 * 约定 code=0 成功，非 0 业务错误；前端拿 res.data 即本对象的 data 字段。
 * <p>
 * NON_NULL：data 为 null 时直接省略字段；前端 0 处使用 timestamp，
 * 留作链路排查用。分页走 data 内的 list/total（见 {@link Page}）；
 * sso 核心旧分页（records/total/page/size）仍用 {@link PageResult}，逐步收敛。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 0 = 成功，其余见 {@link ErrorCode} 与各服务业务码 */
    private int code;
    private String message;
    private T data;
    private long timestamp = System.currentTimeMillis();

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = ErrorCode.OK.getCode();
        r.message = ErrorCode.OK.getMessage();
        r.data = data;
        return r;
    }

    public static <T> R<T> fail(IErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    public static <T> R<T> fail(IErrorCode errorCode, String message) {
        return fail(errorCode.getCode(), message);
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        return r;
    }

    /** 分页数据壳：{ list, total }，与前端 antd-vue Table 的分页用法对齐 */
    @Data
    public static class Page<T> implements Serializable {

        private static final long serialVersionUID = 1L;

        private List<T> list;
        private long total;

        public static <T> Page<T> of(List<T> list, long total) {
            Page<T> p = new Page<>();
            p.list = list;
            p.total = total;
            return p;
        }
    }
}
