package com.laimengchao.sso.ehr.module.employee.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 员工司龄视图（列表 / 核定回显） */
@Data
public class SeniorityVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;
    private String empNo;
    private String name;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;
    private String orgName;
    private LocalDate hireDate;
    private LocalDate seniorityStartDate;
    private LocalDate firstWorkDate;
    /** 本单位司龄（年，1 位小数） */
    private BigDecimal seniorityYears;
    /** 最近一次核定的前司年限 / 累计工龄 */
    private BigDecimal priorYears;
    private BigDecimal totalYears;
    private String source;
    private LocalDateTime confirmedAt;
}
