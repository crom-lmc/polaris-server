package com.laimengchao.sso.common.security;

/**
 * 数据权限根组织编码的缓存接口。
 *
 * <p>设计原则：common 里只定义契约（零 Spring/Redis 依赖），
 * 各模块自己实现 {@code get}/{@code put}/{@code invalidate} 细节。
 *
 * <p>典型实现：ThreadLocal → Redis → Feign/DB 三级缓存。
 */
public interface ScopeCodeCache {

    /** Redis key 前缀 */
    String KEY_PREFIX = "sso:account:scope:";

    /** Redis 默认 TTL（分钟） */
    long DEFAULT_TTL_MINUTES = 10;

    /** 按 accountId 拿 scopeCode（可能返回 null 表示未配置或缓存 miss） */
    String get(Long accountId);

    /** 写入 scopeCode（null 等同删除缓存） */
    void put(Long accountId, String scopeCode);

    /** 主动失效（SSO 账户管理改数据权限范围时调用，通知所有节点下次拉最新） */
    void invalidate(Long accountId);
}
