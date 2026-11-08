package com.laimengchao.sso.auth.service.revocation;

import java.time.Instant;
import java.util.Map;

/**
 * 令牌吊销名单存储抽象。登出 / 刷新轮换时把 jti 记入此处，网关据此拒绝已失效的 token。
 * <p>
 * 两种实现：内存版（单实例够用）与 Redis 版（多实例共享、auth 重启不丢）。
 */
public interface RevocationStore {

    /** 吊销某个 jti，exp 为其自然过期时间（用于惰性清理） */
    void revoke(String jti, Instant exp);

    boolean isRevoked(String jti);

    /** 全量快照（key=jti, value=自然过期时间），供网关拉取 */
    Map<String, Instant> snapshot();
}
