package com.laimengchao.sso.auth.service.session;

/**
 * 中心会话存储。
 * <p>
 * 单点登录的前提是「跨应用共享一个登录态」，而 JWT 存在各应用自己的 localStorage 里做不到这点。
 * 所以登录成功后额外建立一个中心会话，用 HttpOnly cookie 承载 sessionId：
 * 任何应用跳到 SSO 都能凭这个 cookie 认出「你已经登录过」，无需再次输入密码。
 */
public interface SessionStore {

    /** 建立会话：sid -> accountId，带 TTL */
    void put(String sid, Long accountId, long ttlSeconds);

    /** 取会话绑定的账户 ID；不存在或已过期返回 null */
    Long get(String sid);

    /** 主动登出：立即失效（单点登出靠这一步，所有应用下次跳转都要重新登录） */
    void remove(String sid);
}
