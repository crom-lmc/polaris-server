package com.laimengchao.sso.account.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用视图对象。
 * <p>
 * 密钥默认脱敏（{@code appSecretMasked}），只有创建与重置密钥时
 * {@code appSecret} 才带明文——避免列表接口反复把密钥吐给前端。
 */
@Data
public class ApplicationVO {

    /** 雪花ID 转字符串，避免前端 19 位数字丢精度 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String code;
    private String name;
    private String baseUrl;
    /** OAuth2 回调地址（授权码回跳点），前端「进入」按钮构造 /authorize 用 */
    private String redirectUri;
    private String icon;
    private String remark;
    private Integer status;
    private Integer sort;
    private LocalDateTime createdAt;

    /** 是否平台内置应用（统一账号中心）：前端据此隐藏全部操作按钮 */
    private Boolean builtIn;

    /** 脱敏后的密钥，列表展示用 */
    private String appSecretMasked;
    /** 明文密钥，仅创建/重置时返回一次 */
    private String appSecret;
}
