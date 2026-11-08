package com.laimengchao.sso.account.dto.request;

import lombok.Data;

/**
 * 应用新增/编辑请求。{@code appSecret} 不在请求里——由服务端生成，
 * 这样密钥只单向流出（创建/重置时返回一次），不会被前端覆盖成弱值。
 */
@Data
public class ApplicationRequest {

    /** 应用编码，创建后不可修改（ticket 与 JWT 里都用它） */
    private String code;
    private String name;
    private String baseUrl;
    /** OAuth2 回调地址（授权码回跳点），与 baseUrl 同源 */
    private String redirectUri;
    private String icon;
    private String remark;
    /** 1 启用 / 2 停用 */
    private Integer status;
    private Integer sort;
}
