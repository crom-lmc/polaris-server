package com.laimengchao.sso.settings.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 字典项跨服传输对象：与 settings-service 的 /internal/dicts/** 返回结构一致。
 * 字段刻意收窄——调用方只关心 value/label，其余字段仅用于排查对账。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DictItemDTO {

    private String id;

    /** 所属字典类型编码，如 ehr.bank / general.nation */
    private String typeCode;

    /** 取值：业务表里存的就是它，如 icbc */
    private String value;

    /** 显示名：如 工商银行 */
    private String label;

    private Integer sort;

    /** 1 启用 / 0 停用 */
    private Integer status;

    private String remark;
}
