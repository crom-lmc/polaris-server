package com.laimengchao.sso.auth.service;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.InMemoryRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 登录来源 IP 限流，补充账号维度的失败锁定（账号锁定由 sso-account 落库实现）。
 * <p>
 * 账号锁定挡不住「一个 IP 拿字典撞大量账号」，这里按来源 IP 统计失败次数做补充。
 * 单实例内存计数；多实例部署时需换成 Redis。
 */
@Component
public class LoginAttemptGuard {

    private final InMemoryRateLimiter limiter = new InMemoryRateLimiter();

    @Value("${sso.auth.login.ip-max-failures:20}")
    private int ipMaxFailures;

    @Value("${sso.auth.login.ip-window-minutes:10}")
    private int ipWindowMinutes;

    /** 登录前检查；该 IP 已超限直接抛 429。 */
    public void checkAllowed(String ip) {
        if (ip != null && !ip.isBlank() && limiter.isBlocked(ipKey(ip), ipMaxFailures)) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS,
                    "当前网络登录失败次数过多，请稍后再试");
        }
    }

    public void recordFailure(String ip) {
        if (ip != null && !ip.isBlank()) {
            limiter.record(ipKey(ip), ipWindowMinutes * 60_000L);
        }
    }

    private String ipKey(String ip) {
        return "login:ip:" + ip;
    }
}
