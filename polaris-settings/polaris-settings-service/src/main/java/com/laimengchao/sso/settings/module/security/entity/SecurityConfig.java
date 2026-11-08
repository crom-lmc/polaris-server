package com.laimengchao.sso.settings.module.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安全配置（单行，id 固定 1）。
 * 不继承 BaseEntity：全局只有一份配置，不需要雪花 ID / 逻辑删除。
 */
@Data
@TableName("cfg_security_config")
public class SecurityConfig {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 初始密码规则：RANDOM 随机 / FIXED 固定 */
    private String initialPasswordMode;

    /** 固定初始密码（仅 FIXED 模式使用；RANDOM 模式由开户时随机生成） */
    private String defaultPassword;

    /** 密码必须包含的字符类型，逗号分隔：DIGIT/UPPER/LOWER/SPECIAL */
    private String passwordRules;

    /** 密码最小长度 */
    private Integer passwordMinLength;

    /** 密码最大长度 */
    private Integer passwordMaxLength;

    /** 违约密码锁：ACCOUNT 账号密码锁 / EMAIL 邮箱验证锁 */
    private String passwordLockMode;

    /** 登录密码输入失败次数 */
    private Integer loginFailLimit;

    /** 登录失败锁定时间（分钟） */
    private Integer loginFailLockMinutes;

    /** 连续登录检测次数 */
    private Integer loginFailDetectCount;

    /** 首次登录强制修改密码 */
    private Boolean forceChangePassword;

    /** 登录过期时间（分钟） */
    private Integer sessionTimeoutMinutes;

    /** 最大登录错误次数 */
    private Integer maxLoginErrorCount;

    /** 用户锁定时长（分钟） */
    private Integer userLockMinutes;

    /** 邮箱验证码位数 */
    private Integer emailCodeDigits;

    /** 验证码过期时间（分钟） */
    private Integer verifyCodeExpireMinutes;

    /** 最后修改人 */
    private String updatedBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
