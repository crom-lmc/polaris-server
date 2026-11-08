package com.laimengchao.sso.auth.dto;

import lombok.Data;

/** 应用简要信息（会话接口回给前端渲染「我的应用」磁贴用）。 */
@Data
public class AppBrief {

    private Long id;
    private String code;
    private String name;
    private String baseUrl;
    /**
     * OAuth2 回跳地址（授权码模式「进入」按钮构造回调用）。
     * <p>
     * 这里必须下发给前端：sso-web 的「进入」直接以当前 Bearer 调 {@code /oauth/authorize}
     * 静默签码，后端 {@link com.laimengchao.sso.auth.service.oauth.OAuthService#buildRedirect}
     * 会把 {@code ?code=} 追加到 redirectUri <b>末尾</b>——
     * hash 路由的应用（如 blog-web，createWebHashHistory）需要把 {@code #/sso/callback}
     * 放进 redirectUri，否则 code 落在真实 query 里、hash 路由读不到，单点会弹回登录页。
     * 缺失时 sso-web 退化为 baseUrl，history 路由应用（cms）仍能读到根 query 里的 code，
     * 但 hash 路由应用必失败，因此这里务必随 baseUrl 一起下发。
     */
    private String redirectUri;
    private String icon;
}
