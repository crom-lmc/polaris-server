package com.laimengchao.sso.settings.module.embed.dto;

import lombok.Data;

/**
 * 埋点编辑入参：只允许改描述、JSON 值、归属应用/模块与排序，key 不可改。
 * 字段为 null 表示本次不修改。
 */
@Data
public class EmbedPointPatch {
    private String appCode;
    private String module;
    private String name;
    /** 埋点值（必须是合法 JSON） */
    private String value;
    private Integer sort;
}
