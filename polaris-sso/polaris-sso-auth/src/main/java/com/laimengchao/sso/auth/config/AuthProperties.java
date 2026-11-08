package com.laimengchao.sso.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证服务配置。私钥<b>不再打包进 jar</b>：由 Nacos 下发（通常为 {@code ENC(...)} 密文），
 * 支持内联 PEM 文本或 {@code classpath:}/{@code file:} 地址，见 KeySources。
 */
@Data
@ConfigurationProperties(prefix = "sso.auth")
public class AuthProperties {

    /** 签发者标识，写入 iss，验签方可据此校验来源 */
    private String issuer = "sso-auth";

    /** access token 有效期（秒），默认 2 小时 */
    private long accessTtlSeconds = 7200L;

    /** refresh token 有效期（秒），默认 30 天 */
    private long refreshTtlSeconds = 2592000L;

    /** 私钥（PKCS#8 PEM 内联文本，或 classpath:/file: 地址）；缺失即启动失败，不再有 jar 内兜底 */
    private String privateKey = "";

    /** 公钥（X.509 PEM 内联文本，或 classpath:/file: 地址），auth 校验 refresh token 时用；
     *  默认取包内公钥（公钥不敏感），Nacos 里配了同名项则覆盖，便于 prod 换独立密钥对 */
    private String publicKey = "classpath:keys/jwt-public.pem";

    /** 直连 HTTP 客户端（仅 WechatService 使用）的超时（毫秒） */
    private int accountTimeoutMillis = 3000;

    // ---------------- 邮箱验证码登录 ----------------
    /** 验证码 TTL（分钟） */
    private int emailCodeTtlMinutes = 5;
    /** SMTP 主机，未配置则走控制台打印（本地调试） */
    private String smtpHost = "";
    private int smtpPort = 465;
    private String smtpUser = "";
    private String smtpPassword = "";
    /** 发件人显示名与地址 */
    private String mailFrom = "";
    /** 是否启用真正的邮件发送：smtpHost 非空即视为启用 */
    public boolean isEmailEnabled() {
        return smtpHost != null && !smtpHost.isBlank();
    }

    // ---------------- 吊销名单存储 ----------------
    /** 吊销名单存储：memory（默认，单实例） / redis（多实例共享、重启不丢） */
    private String revocationStore = "memory";
    public boolean isRevocationRedis() {
        return "redis".equalsIgnoreCase(revocationStore);
    }

    // ---------------- 微信扫码登录（M4） ----------------
    /** 微信开放平台 appid；空值表示未配置，/wechat/login 返回 501 */
    private String wechatAppid = "";
    /** 微信开放平台 appsecret；私钥级，生产应走 Nacos ENC */
    private String wechatSecret = "";
    /** 微信登录回调地址（与开放平台后台一致） */
    private String wechatRedirectUri = "";
    public boolean isWechatEnabled() {
        return wechatAppid != null && !wechatAppid.isBlank();
    }

    // ---------------- 中心会话与跨应用单点 ----------------
    /** 中心会话存储：redis（多实例共享）/ memory（本地单实例兜底） */
    private String sessionStore = "redis";
    public boolean isSessionRedis() {
        return "redis".equalsIgnoreCase(sessionStore);
    }

    /** 中心会话 TTL（秒），默认 7 天；这是「一次登录、多应用免登」的窗口 */
    private long sessionTtlSeconds = 604800L;

    /** 会话 cookie 名 */
    private String sessionCookieName = "SSO_SID";

    /**
     * 会话 cookie 的 Domain。
     * <p>生产必须是顶级域（如 {@code .laimengchao.site}），否则 blog/sso 两个子域
     * 拿不到同一个 cookie，单点立刻退化成各登录各的。本地留空即可（host-only）。
     */
    private String sessionCookieDomain = "";

    /** 是否只在 HTTPS 下发送 cookie；本地 http 调试必须为 false */
    private boolean sessionCookieSecure = false;

    /**
     * 会话 cookie 的 SameSite。
     * <p>默认 Lax：跨站顶层跳转（点链接进 sso）能带上，跨站 XHR 不带——
     * 既能支撑单点跳转，又挡住大部分 CSRF。改成 Strict 会让「从应用跳回 sso」
     * 丢掉会话，单点直接失效。
     */
    private String sessionCookieSameSite = "Lax";

    /** 一次性 ticket TTL（秒），默认 60；换完即删，只允许用一次 */
    private long ticketTtlSeconds = 60L;

    /** 允许的跳转来源是否严格限定在应用 baseUrl 的 origin（关掉会有开放重定向风险，别关） */
    private boolean ticketStrictRedirect = true;

    // ---------------- OAuth2 标准授权码流程（A 档） ----------------
    /** 授权码 TTL（秒），默认 300（协议建议 ≤10 分钟）；单次有效 */
    private long oauthCodeTtlSeconds = 300L;

    /** 授权码存储：redis（多实例共享）/ memory（本地单实例兜底） */
    private String oauthCodeStore = "redis";

    /** OAuth2 回跳是否严格限定在应用 baseUrl 的 origin（与 ticket 同一套安全策略） */
    private boolean oauthStrictRedirect = true;
}
