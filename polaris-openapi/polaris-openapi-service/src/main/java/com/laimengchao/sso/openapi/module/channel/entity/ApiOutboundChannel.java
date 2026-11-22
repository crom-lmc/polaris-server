package com.laimengchao.sso.openapi.module.channel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 出站渠道配置实体。
 * <p>
 * 敏感凭证（SMTP 账号/口令/授权码）以 AES-GCM 密文存 {@code credential_ref}，
 * 明文既不入库也不对外回显；host/port/from 等非敏感项放 {@code auth_config}。
 */
@Data
@TableName("api_outbound_channel")
public class ApiOutboundChannel {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String channelCode;
    private String channelName;
    private String baseUrl;
    private String authType;
    /** 凭证密文 ENC(base64)，明文不入库 */
    private String credentialRef;
    /** 非敏感认证配置 JSON：邮件渠道存 host/port/from/ssl */
    private String authConfig;
    private Integer timeoutMs;
    private Integer retryMax;
    /** 允许哪些内部 app 使用，逗号分隔；空=不限制 */
    private String allowedApps;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
