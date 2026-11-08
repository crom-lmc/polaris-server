package com.laimengchao.sso.account.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 下游应用注册信息。
 * <p>
 * {@code appSecret} 是应用后端调用 {@code /api/auth/ticket/exchange} 时的身份凭据：
 * 只有持有密钥的应用才能把一次性 ticket 换成 JWT，否则 ticket 一旦泄露就等于 token 泄露。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sso_application")
public class SsoApplication extends BaseEntity {

    /** 应用编码，如 blog-admin（唯一，写入 ticket 与 JWT 的 apps claim） */
    private String code;
    private String name;
    /** 应用访问地址，前端「去应用」跳转用 */
    private String baseUrl;
    /**
     * OAuth2 回调地址（授权码回跳点）。
     * <p>
     * 与 {@code baseUrl} 同源即可（{@code issue()} 的 {@code isAllowedRedirect} 只校验 origin）。
     * 历史路由应用形如 {@code http://host:port/callback}，哈希路由应用形如
     * {@code http://host:port/#/sso/callback}。前端「进入」按钮用它构造
     * {@code /authorize?redirect_uri=...}，由账号中心静默签码后回跳。
     */
    private String redirectUri;
    /** 应用密钥（32 位随机串），仅创建/重置时明文返回一次 */
    private String appSecret;
    private String icon;
    private String remark;
    /** 1 启用 / 2 停用；停用后不再签发该应用的 ticket */
    private Integer status;
    private Integer sort;
}
