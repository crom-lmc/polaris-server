package com.laimengchao.sso.auth.service.revocation;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版吊销名单。单实例模板的默认实现；进程重启即清空。
 */
@Slf4j
public class MemoryRevocationStore implements RevocationStore {

    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    @Override
    public void revoke(String jti, Instant exp) {
        if (jti != null && exp != null) {
            revoked.put(jti, exp);
            purgeExpired();
        }
    }

    @Override
    public boolean isRevoked(String jti) {
        return jti != null && revoked.containsKey(jti);
    }

    @Override
    public Map<String, Instant> snapshot() {
        purgeExpired();
        return Map.copyOf(revoked);
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        revoked.entrySet().removeIf(e -> e.getValue().isBefore(now));
    }
}
