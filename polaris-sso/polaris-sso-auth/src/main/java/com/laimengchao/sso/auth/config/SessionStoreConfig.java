package com.laimengchao.sso.auth.config;

import com.laimengchao.sso.auth.service.session.MemorySessionStore;
import com.laimengchao.sso.auth.service.session.RedisSessionStore;
import com.laimengchao.sso.auth.service.session.SessionStore;
import com.laimengchao.sso.auth.service.ticket.MemoryTicketStore;
import com.laimengchao.sso.auth.service.ticket.RedisTicketStore;
import com.laimengchao.sso.auth.service.ticket.TicketStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 中心会话 / 一次性票据的存储选型。
 * <p>
 * 选 Redis 时会先做一次 ping 探活：连不上就退回内存版并打警告，
 * 保证「没有 Redis 的环境也能把服务跑起来」这条底线不被破坏——
 * 代价是多实例下单点会退化，日志里能看出来。
 */
@Slf4j
@Configuration
public class SessionStoreConfig {

    @Bean
    public SessionStore sessionStore(AuthProperties props,
                                     ObjectProvider<StringRedisTemplate> redisProvider) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (props.isSessionRedis() && redisAlive(redis)) {
            log.info("中心会话存储：Redis（多实例共享，TTL {}s）", props.getSessionTtlSeconds());
            return new RedisSessionStore(redis);
        }
        log.warn("中心会话存储：内存（单实例；多实例部署下跨应用单点不可靠，请启用 Redis）");
        return new MemorySessionStore();
    }

    @Bean
    public TicketStore ticketStore(AuthProperties props,
                                   ObjectProvider<StringRedisTemplate> redisProvider) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (props.isSessionRedis() && redisAlive(redis)) {
            return new RedisTicketStore(redis);
        }
        return new MemoryTicketStore();
    }

    /** 一次轻量 ping；Redis 没起时不要让整个服务启动失败 */
    private boolean redisAlive(StringRedisTemplate redis) {
        if (redis == null) {
            return false;
        }
        try {
            String pong = redis.execute(conn -> conn.ping(), true);
            return pong != null;
        } catch (Exception e) {
            log.warn("Redis 不可用（{}），会话将退回内存存储", e.getMessage());
            return false;
        }
    }
}
