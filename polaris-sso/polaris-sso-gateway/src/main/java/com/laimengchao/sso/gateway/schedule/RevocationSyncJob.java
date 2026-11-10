package com.laimengchao.sso.gateway.schedule;

import com.laimengchao.sso.gateway.support.RevocationCache;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 吊销名单同步：默认每 5 秒从 sso-auth 拉取一次已登出的 jti，
 * 间隔可由 {@code sso.gateway.revocation-sync-millis} 覆盖。
 */
@Component
@RequiredArgsConstructor
public class RevocationSyncJob {

    private final RevocationCache revocationCache;

    @Scheduled(initialDelayString = "3000",
            fixedDelayString = "${sso.gateway.revocation-sync-millis:5000}")
    public void sync() {
        revocationCache.sync();
    }
}
