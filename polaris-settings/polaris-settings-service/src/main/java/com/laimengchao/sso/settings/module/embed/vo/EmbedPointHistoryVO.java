package com.laimengchao.sso.settings.module.embed.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 埋点配置历史项 */
@Data
public class EmbedPointHistoryVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long pointId;
    private String code;
    /** 本次保存后的 value 快照 */
    private String value;
    private Long operatedBy;
    private String operatedName;
    private LocalDateTime createdAt;
}
