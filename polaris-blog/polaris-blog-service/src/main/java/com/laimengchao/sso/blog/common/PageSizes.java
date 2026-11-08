package com.laimengchao.sso.blog.common;

/**
 * 分页大小钳制。
 *
 * <p>原来每个 Controller 都写 {@code size != null && size > 0 ? size : 设置值}，
 * 没有上限——读者端公开列表根本不需要登录，一个 {@code ?size=100000}
 * 就能让 MySQL 一次性吐出全表并塞进内存。这里统一封顶，各处只调这一个方法。
 */
public final class PageSizes {

    /** 单页上限：够用，且不会因为一个参数把服务打穿 */
    public static final long MAX = 100;

    private PageSizes() {
    }

    /**
     * @param size     请求里的每页条数，可为空/非法
     * @param fallback 站点设置里的默认值
     * @return 落在 [1, MAX] 的条数，默认值本身也被钳制
     */
    public static long resolve(Long size, long fallback) {
        long def = fallback > 0 ? Math.min(fallback, MAX) : 10;
        if (size == null || size <= 0) {
            return def;
        }
        return Math.min(size, MAX);
    }
}
