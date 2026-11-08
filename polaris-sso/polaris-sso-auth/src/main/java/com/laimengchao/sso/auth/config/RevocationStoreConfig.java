package com.laimengchao.sso.auth.config;

import com.laimengchao.sso.auth.service.revocation.MemoryRevocationStore;
import com.laimengchao.sso.auth.service.revocation.RedisRevocationStore;
import com.laimengchao.sso.auth.service.revocation.RevocationStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 吊销名单存储选型：配置了 Redis 且显式开启 {@code sso.auth.revocation-store=redis} 时用 Redis 版，
 * 否则内存版兜底。这样没有 Redis 的环境也能直接跑起来。
 */
@Slf4j
@Configuration
public class RevocationStoreConfig {

    @Bean
    public RevocationStore revocationStore(AuthProperties props,
                                           ObjectProvider<StringRedisTemplate> redisProvider) {
        if (props.isRevocationRedis() && redisProvider.getIfAvailable() != null) {
            log.info("吊销名单存储：Redis（多实例共享）");
            return new RedisRevocationStore(redisProvider.getIfAvailable());
        }
        log.info("吊销名单存储：内存（单实例；生产建议 Redis）");
        return new MemoryRevocationStore();
    }
}
