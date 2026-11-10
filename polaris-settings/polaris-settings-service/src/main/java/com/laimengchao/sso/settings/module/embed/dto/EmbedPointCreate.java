package com.laimengchao.sso.settings.module.embed.dto;

import lombok.Data;

/**
 * 新增埋点入参：key / 所属应用 / 模块 / 描述 / value 都可维护；
 * 新增后 key、所属应用、模块不可再改（编辑只放开描述与 value）。
 */
@Data
public class EmbedPointCreate {
    /** 埋点 key，唯一，如 ehr.xxx.yyy */
    private String code;
    /** 所属应用编码，空=通用 */
    private String appCode;
    /** 模块（左侧树叶子），空则归入「未分类」 */
    private String module;
    /** 描述 */
    private String name;
    /** 埋点值（必须是合法 JSON） */
    private String value;
    private Integer sort;
}
