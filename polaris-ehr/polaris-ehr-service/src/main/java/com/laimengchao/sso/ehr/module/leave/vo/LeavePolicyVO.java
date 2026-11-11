package com.laimengchao.sso.ehr.module.leave.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** 城市假期政策：婚假 / 产假 / 陪产假的天数与计算方式，以及按开始日期试算的结束日期 */
@Data
public class LeavePolicyVO {

    /** 员工所属二级单位城市（未找到时为空） */
    private String city;
    private Map<String, Policy> policies = new LinkedHashMap<>();
    /** 试算结果：假期类型 */
    private String type;
    /** 试算结果：固定天数 */
    private BigDecimal days;
    /** 试算结果：NATURAL / WORKDAY */
    private String dayMode;
    /** 试算结果：结束日期 */
    private LocalDate endDate;

    @Data
    @AllArgsConstructor
    public static class Policy {
        private BigDecimal days;
        private String dayMode;
    }
}
