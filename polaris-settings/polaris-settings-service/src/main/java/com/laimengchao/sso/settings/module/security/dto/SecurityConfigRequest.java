package com.laimengchao.sso.settings.module.security.dto;

import lombok.Data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 保存安全配置入参。
 * 密码规则在前端是多选（数字/大写/小写/特殊字符），落库时拼成逗号分隔字符串。
 */
@Data
public class SecurityConfigRequest {

    /** 初始密码规则：RANDOM 随机 / FIXED 固定 */
    @NotBlank(message = "初始密码规则不能为空")
    private String initialPasswordMode;

    /** 固定初始密码（仅 FIXED 模式必填；RANDOM 模式忽略，由开户时随机生成） */
    @Size(max = 64, message = "默认密码长度不能超过 64")
    private String defaultPassword;

    /** 密码必须包含的字符类型（多选）：DIGIT/UPPER/LOWER/SPECIAL */
    @NotEmpty(message = "密码规则至少选择一项")
    private List<String> passwordRules;

    @NotNull(message = "密码最小长度不能为空")
    @Min(value = 6, message = "密码最小长度不能小于 6")
    @Max(value = 32, message = "密码最小长度不能超过 32")
    private Integer passwordMinLength;

    @NotNull(message = "密码最大长度不能为空")
    @Min(value = 6, message = "密码最大长度不能小于 6")
    @Max(value = 64, message = "密码最大长度不能超过 64")
    private Integer passwordMaxLength;

    /** 违约密码锁：ACCOUNT 账号密码锁 / EMAIL 邮箱验证锁 */
    @NotBlank(message = "违约密码锁不能为空")
    private String passwordLockMode;

    @NotNull @Min(1) @Max(20)
    private Integer loginFailLimit;

    @NotNull @Min(0) @Max(1440)
    private Integer loginFailLockMinutes;

    @NotNull @Min(1) @Max(50)
    private Integer loginFailDetectCount;

    @NotNull private Boolean forceChangePassword;

    @NotNull @Min(1) @Max(10080)
    private Integer sessionTimeoutMinutes;

    @NotNull @Min(1) @Max(50)
    private Integer maxLoginErrorCount;

    @NotNull @Min(0) @Max(1440)
    private Integer userLockMinutes;

    @NotNull(message = "邮箱验证码位数不能为空")
    @Min(value = 4, message = "邮箱验证码位数不能小于 4")
    @Max(value = 10, message = "邮箱验证码位数不能超过 10")
    private Integer emailCodeDigits;

    @NotNull @Min(1) @Max(120)
    private Integer verifyCodeExpireMinutes;

}
