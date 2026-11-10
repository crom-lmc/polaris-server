package com.laimengchao.sso.settings.module.embed.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 埋点配置列表项 */
@Data
public class EmbedPointVO {
    /** 雪花ID序列化成字符串，前端编辑/查历史回传时不丢精度 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String code;
    /** 所属应用编码，空=通用 */
    private String appCode;
    private String module;
    private String name;
    /** 埋点值（JSON 文本） */
    private String value;
    private Integer builtIn;
    private Integer sort;
    private LocalDateTime updatedAt;
}
