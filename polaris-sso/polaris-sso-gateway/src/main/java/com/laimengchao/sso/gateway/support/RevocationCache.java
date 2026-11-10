package com.laimengchao.sso.gateway.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.laimengchao.sso.gateway.config.GatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 吊销名单的本地副本。
 * <p>
 * 网关不查库、不连 Redis，靠定时从 sso-auth 拉取「已登出的 jti」来近似实时地
 * 拒绝已作废的 access token。代价是同步间隔内的延迟（默认 5 秒）——这是
 * 「不引入共享存储」能拿到的最好效果；生产建议换成 Redis 或 auth 主动推送。
 * <p>
 * 拉取失败时保留旧副本（不清空），避免 auth 短暂抖动导致已登出的票重新可用。
 */
@Slf4j
@Component
public class RevocationCache {

    private final GatewayProperties props;
    private final Set<String> revoked = ConcurrentHashMap.newKeySet();
    private final WebClient client;

    /**
     * authBaseUrl 形如 {@code lb://sso-auth} 时走负载均衡 WebClient，
     * 形如 {@code http://127.0.0.1:9001} 时走普通 WebClient（直连模式不需要注册中心）。
     */
    public RevocationCache(GatewayProperties props,
                           @Qualifier("plainWebClientBuilder") WebClient.Builder plain,
                           @Qualifier("lbWebClientBuilder") WebClient.Builder lb) {
        this.props = props;
        String base = props.getAuthBaseUrl();
        boolean useLb = base != null && base.startsWith("lb://");
        this.client = (useLb ? lb : plain).baseUrl(base).build();
        log.info("吊销名单同步目标：{}（{}）", base, useLb ? "服务发现" : "直连");
    }

    /** 拉取一次吊销名单（调度入口见 schedule 包的 RevocationSyncJob） */
    public void sync() {
        client.get().uri("/api/auth/internal/revoked")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .subscribe(this::apply, err -> log.warn("同步吊销名单失败（保留旧副本）: {}", err.toString()));
    }

    private void apply(JsonNode body) {
        JsonNode items = body.path("data").path("items");
        if (!items.isObject()) {
            return;
        }
        Set<String> fresh = new HashSet<>();
        items.fieldNames().forEachRemaining(fresh::add);
        revoked.retainAll(fresh);
        revoked.addAll(fresh);
    }

    public boolean isRevoked(String jti) {
        return jti != null && revoked.contains(jti);
    }
}
