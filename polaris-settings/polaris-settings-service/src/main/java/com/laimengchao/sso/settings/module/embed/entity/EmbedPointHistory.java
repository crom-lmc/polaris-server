package com.laimengchao.sso.settings.module.embed.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 埋点配置历史：每次保存写一条保存后的快照，用于「历史记录」查看。
 */
@Data
@TableName("cfg_embed_point_history")
public class EmbedPointHistory {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /** 埋点ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long pointId;
    private String code;
    private String appCode;
    private String module;
    private String name;
    /** 本次保存后的 value 快照 */
    private String value;
    /** 操作人账号ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatedBy;
    /** 操作人名称 */
    private String operatedName;
    private LocalDateTime createdAt;
}
