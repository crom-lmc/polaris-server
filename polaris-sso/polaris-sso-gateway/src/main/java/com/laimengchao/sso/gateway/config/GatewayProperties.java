package com.laimengchao.sso.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "sso.gateway")
public class GatewayProperties {

    /** 必须与 sso-auth 的 issuer 一致，否则签发的票在网关全被判为无效 */
    private String issuer = "sso-auth";

    /** 只放公钥：网关能验签但不能签发，私钥泄露风险被限制在 auth 一个服务。
     *  支持 classpath:/file: 地址或内联 PEM 文本（prod 用独立密钥对时由 Nacos 下发） */
    private String publicKey = "classpath:keys/jwt-public.pem";

    /**
     * 免鉴权路径。
     * <p>
     * 支持两种写法：精确路径（{@code /api/auth/login}）与前缀通配（{@code /api/blog/public/**}）。
     * 业务应用的读者端接口天生不需要登录，必须能按前缀放行——否则每加一个公开接口
     * 都要改一次网关配置，早晚会漏。
     */
    private List<String> whitelist = new ArrayList<>(List.of(
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/email/code",
            "/api/auth/email/login",
            "/api/auth/wechat/login",
            // 单点相关：/session 与 /ticket 靠 HttpOnly cookie 识别身份（跨应用共享登录态的前提，
            // 不能要求 Bearer）；/ticket/exchange 靠应用 appSecret 识别调用方，也不带用户 token
            "/api/auth/session",
            "/api/auth/session/token",
            "/api/auth/ticket",
            "/api/auth/ticket/exchange",
            // OAuth2 标准端点：/authorize 与 /ticket 一样靠中心会话 cookie 识别身份；
            // /token 是应用后端到服务器的调用，靠 client_secret/PKCE 准入，不带用户令牌
            "/api/auth/oauth/authorize",
            "/api/auth/oauth/token",
            // 公钥分发：公钥本身是公开信息，业务应用（尤其是没跑在本网关后面的）
            // 要靠它自校验令牌，不能要求先有令牌才能取
            "/api/auth/public/jwks",
            "/api/auth/public/public-key",
            // 业务应用（blog-admin）的读者端：前台不需要登录，公开接口按前缀放行
            "/api/blog/public/**",
            // 单点换票：此时用户还没有博客令牌，凭一次性 ticket 换（应用密钥在后端）
            "/api/blog/sso/**",
            // 业务应用（cms）的读者端与换票端：同 blog 的口径
            "/api/cms/public/**",
            "/api/cms/sso/**",
            // CMS 媒体文件：静态资源，读者端直接加载（经 /api/cms/** 路由转发到 9004）
            "/api/cms/media/file/**",
            // 业务应用（portal）的读者端与换票端：同 cms/blog 口径
            "/api/portal/public/**",
            "/api/portal/sso/**",
            // 业务应用（ehr）的换票端：同 cms/blog/portal 口径
            // 漏了这条 → /callback 拿授权码换令牌时被网关判「未登录」，登录页停在「登录失败」
            "/api/ehr/sso/**",
            // 业务应用（settings）的换票端：同 ehr 口径（漏了同样会「未登录，请先获取访问令牌」）
            "/api/settings/sso/**",
            // 上传的图片/附件：静态资源，读者端也要能直接加载
            "/uploads/**",
            "/actuator/health"
    ));

    /** 内部接口前缀：由网关直接拒绝，只允许服务间内网直连 */
    private List<String> internalPrefixes = new ArrayList<>(List.of("/api/auth/internal/"));

    /** sso-auth 基址，用于同步吊销名单 */
    private String authBaseUrl = "http://127.0.0.1:9001";

    /** 吊销名单同步间隔（毫秒）；越小登出越及时，越大对 auth 压力越小 */
    private long revocationSyncMillis = 5000L;
}
