package com.laimengchao.sso.settings.client.dto;

import lombok.Data;

/** 埋点配置（配置中心下发的最小结构） */
@Data
public class EmbedPointDTO {
    /** 埋点 key，如 ehr.account.default-roles */
    private String code;
    /** 所属应用编码，空=通用 */
    private String appCode;
    /** 描述 */
    private String name;
    /** 埋点值（JSON 文本） */
    private String value;
}
