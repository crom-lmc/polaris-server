package com.laimengchao.sso.ehr.module.attendance.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 加班列表展示。 */
@Data
public class OvertimeVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;
    private String employeeName;
    private LocalDate workDate;
    /** 加班时段 HH:mm ~ HH:mm */
    private LocalTime startTime;
    private LocalTime endTime;
    /** 有效加班小时数（扣除工作时段重叠后） */
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
    /** 审批中心在途实例 ID（仅 status=0 时有值，供前端点开审批详情抽屉） */
    private String approvalInstanceId;
}
