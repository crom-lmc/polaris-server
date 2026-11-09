package com.laimengchao.sso.ehr.module.attendance.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 加班列表展示。 */
@Data
public class OvertimeVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;
    private String employeeName;
    private LocalDate workDate;
    private BigDecimal hours;
    private String type;
    private String typeName;
    private String reason;
    private Integer status;
    private String statusName;
    private String approveNote;
    private LocalDateTime createdAt;
    /** 是否本人提交：控制撤销按钮 */
    private Boolean mine;
}
