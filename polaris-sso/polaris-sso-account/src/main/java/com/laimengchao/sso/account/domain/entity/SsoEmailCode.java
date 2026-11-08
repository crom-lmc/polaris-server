package com.laimengchao.sso.account.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邮箱验证码。只存 BCrypt 哈希，禁止明文；带过期与尝试次数。
 */
@Data
@TableName("sso_email_code")
public class SsoEmailCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String email;
    /** 验证码 BCrypt 哈希 */
    private String codeHash;
    /** login 等场景 */
    private String scene;
    private String ip;
    private LocalDateTime expiresAt;
    /** 0=未使用 1=已使用 */
    private Integer used;
    /** 校验尝试次数 */
    private Integer tryCount;
    private LocalDateTime createdAt;
}
