package com.laimengchao.sso.auth.service.oauth;

/**
 * OAuth2 授权码存储（A 档：自研标准授权码流程，签发端仍是 RS256 JWT）。
 * <p>
 * 授权码与 ticket 同构：短命、一次性、取走即删。区别在于 ticket 是「SSO 自己跳自己」
 * 的内部通行证，而授权码是<b>标准协议形状</b>的凭据——第三方可以用任何标准 OAuth2 客户端
 * 库接入；同时支持 PKCE（code_challenge），公共客户端不持密钥也能安全换令牌。
 */
public interface OAuthCodeStore {

    void save(String code, CodePayload payload, long ttlSeconds);

    /** 原子取出并删除；不存在/已过期/重复使用一律 null */
    CodePayload take(String code);

    /**
     * 授权码内容。
     *
     * @param accountId           授权主体
     * @param clientId            应用编码（= sso_application.code）
     * @param scope               授权时请求的 scope（空串表示未指定）
     * @param redirectUri         授权时使用的回跳地址（换码时必须逐字节一致，防串包）
     * @param codeChallenge       PKCE 挑战值（可空）
     * @param codeChallengeMethod PKCE 方法：S256 / plain（可空）
     * @param sid                 授权时所在的中心会话（可空）；换出的令牌继承它，
     *                            这样 SSO 一侧登出才能连带作废应用手里的令牌
     */
    record CodePayload(Long accountId, String clientId, String scope, String redirectUri,
                       String codeChallenge, String codeChallengeMethod, String sid) {

        /** 兼容旧构造（无会话） */
        public CodePayload(Long accountId, String clientId, String scope, String redirectUri,
                           String codeChallenge, String codeChallengeMethod) {
            this(accountId, clientId, scope, redirectUri, codeChallenge, codeChallengeMethod, null);
        }
    }
}
