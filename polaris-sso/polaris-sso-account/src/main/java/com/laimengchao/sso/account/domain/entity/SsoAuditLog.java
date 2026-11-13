package com.laimengchao.sso.account.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** SSO 敏感操作审计，只新增与查询，不做逻辑删除。 */
@Data
@TableName("sso_audit_log")
public class SsoAuditLog {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;
    private String operatorName;
    private String action;
    private String targetType;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;
    /** result=success/fail 及非敏感操作详情，不记录密码或密码哈希。 */
    private String fields;
    private String ip;
    private String ua;
    private LocalDateTime createdAt;
}
