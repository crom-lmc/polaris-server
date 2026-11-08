package com.laimengchao.sso.auth.service.ticket;

/**
 * 一次性票据（ticket）存储。
 * <p>
 * 单点的核心动作：SSO 把「你是谁」压缩成一张短命票据交回应用，
 * 应用后端再拿票据换 JWT。票据必须<b>一次性</b>（取走即删），
 * 否则被中间人截获就能反复换 token。
 */
public interface TicketStore {

    void save(String ticket, TicketPayload payload, long ttlSeconds);

    /** 原子取出并删除；不存在或已过期返回 null（重复使用必然拿不到） */
    TicketPayload take(String ticket);

    /**
     * 票据内容：归属账户 + 目标应用编码 + 所属中心会话。
     *
     * @param sid 换出的令牌要继续绑定的中心会话 ID；为空表示这张票不是从某个
     *            浏览器会话签发的（例如服务间补签），换出的令牌也就不参与集体登出
     */
    record TicketPayload(Long accountId, String appCode, String sid) {

        /** 兼容旧构造（无会话） */
        public TicketPayload(Long accountId, String appCode) {
            this(accountId, appCode, null);
        }
    }
}
