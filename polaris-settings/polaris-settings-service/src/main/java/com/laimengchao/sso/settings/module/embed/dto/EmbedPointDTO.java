package com.laimengchao.sso.settings.module.embed.dto;

import lombok.Data;

/**
 * 跨服务下发的最小埋点结构：其他应用只关心 key 与 JSON 值。
 */
@Data
public class EmbedPointDTO {
    private String code;
    private String appCode;
    private String name;
    /** 埋点值（JSON 文本） */
    private String value;
}
