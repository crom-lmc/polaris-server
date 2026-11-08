package com.laimengchao.sso.auth.config;

import com.laimengchao.sso.auth.service.oauth.MemoryOAuthCodeStore;
import com.laimengchao.sso.auth.service.oauth.OAuthCodeStore;
import com.laimengchao.sso.auth.service.oauth.RedisOAuthCodeStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * OAuth2 授权码存储选型：与 ticket/session 同一套开关思路——
 * 配了 Redis 就共享（多实例、重启不丢），否则内存兜底。
 */
@Slf4j
@Configuration
public class OAuthStoreConfig {

    @Bean
    public OAuthCodeStore oauthCodeStore(AuthProperties props,
                                         ObjectProvider<StringRedisTemplate> redisProvider) {
        if ("redis".equalsIgnoreCase(props.getOauthCodeStore()) && redisProvider.getIfAvailable() != null) {
            log.info("OAuth2 授权码存储：Redis（多实例共享）");
            return new RedisOAuthCodeStore(redisProvider.getIfAvailable());
        }
        log.info("OAuth2 授权码存储：内存（单实例；生产建议 Redis）");
        return new MemoryOAuthCodeStore();
    }
}
