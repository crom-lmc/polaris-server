package com.laimengchao.sso.common.security;

/**
 * JWT 载荷与网关注入头的共享约定。
 * <p>
 * 只有“名字”放这里，不引入 jjwt 依赖——sso-common 保持与框架无关。
 * 签发端在 sso-auth、验签端在 sso-gateway，两端都引用本类，避免字符串写飘。
 */
public final class JwtClaims {

    private JwtClaims() {
    }

    /** 账户 ID（JWT 的 sub） */
    public static final String ACCOUNT_ID = "sub";
    /** 登录名 */
    public static final String USERNAME = "uname";
    /** 昵称 */
    public static final String NICKNAME = "nick";
    /** 是否超级管理员，布尔值 */
    public static final String SUPER_ADMIN = "sa";
    /** 按钮/菜单权限码集合，逗号分隔字符串（体量小，放 token 里可免去下游查库） */
    public static final String PERMISSIONS = "perms";
    /**
     * 权限码所属的应用编码。同一账号在不同应用里权限不同，令牌必须标明作用域，
     * 否则业务应用会看到平台自身的按钮码（既泄露又容易误判）。
     */
    public static final String APP = "app";
    /** 平台自身的应用编码：管理台、内置权限码都属于它 */
    public static final String PLATFORM_APP = "sso";
    /** 可访问的应用编码集合，逗号分隔字符串（应用级授权，与 perms 正交） */
    public static final String APPS = "apps";
    /** token 类型：access / refresh */
    public static final String TOKEN_TYPE = "typ";
    /** token 唯一 ID，用于吊销与刷新轮换 */
    public static final String TOKEN_ID = "jti";
    /**
     * 中心会话 ID（HttpOnly cookie 里那个 sid）。
     * <p>
     * 它把「同一个浏览器里各个应用的令牌」串成一条命：一次登录建一个 sid，
     * 后续无论走 ticket 还是授权码换出来的令牌都继承同一个 sid，
     * 于是任意一端登出时只需作废 sid，其余应用手里的令牌立刻集体失效。
     * 没有这一维，登出只能吊销当前应用那一对 jti，别的应用照旧能用——就是「一个系统退了、
     * 另一个还在登录」的根因。
     */
    public static final String SESSION_ID = "sid";
    /**
     * 吊销名单里 sid 的 key 前缀。
     * <p>jti 与 sid 同处一个集合，靠前缀隔开，网关与 auth 必须用同一个常量。
     */
    public static final String SID_REVOKE_PREFIX = "sid:";

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    /**
     * 网关注入给下游的可信头。
     * <p>
     * 约束：这些头**只能**由网关写入。网关必须先剥离客户端自带的同名头，否则
     * 任何人都能伪造 {@code X-Account-Id} 冒充超级管理员。
     */
    public static final class Headers {
        private Headers() {
        }

        public static final String ACCOUNT_ID = "X-Account-Id";
        public static final String USERNAME = "X-Account-Name";
        /** 注意：HTTP 头默认按 ASCII 处理，中文昵称会乱码，故不注入昵称头 */
        public static final String SUPER_ADMIN = "X-Account-Super";
        public static final String PERMISSIONS = "X-Account-Perms";
        /** 可访问的应用编码（逗号分隔）。下游应用据此判断「这个用户能不能进我这儿」 */
        public static final String APPS = "X-Account-Apps";
        /** 请求目标应用编码：由调用方网关/反代写入，用于下游二次校验 apps 是否包含自己 */
        public static final String APP_CODE = "X-App-Code";

        /** 内部调用令牌：服务间直连（绕过网关）时必须携带，防止内网被随意调用 */
        public static final String INTERNAL_TOKEN = "X-Internal-Token";
    }
}
