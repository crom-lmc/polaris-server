package com.laimengchao.sso.ehr.module.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 加班申请：审批通过后按小时折算调休额度。 */
@Data
@TableName("ehr_overtime")
public class Overtime {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long employeeId;
    private Long accountId;
    private LocalDate workDate;
    private BigDecimal hours;
    /** NORMAL 工作日 / HOLIDAY 节假日 / RESTDAY 休息日 */
    private String type;
    private String reason;
    /** 0 待审批 / 1 已通过 / 2 已驳回 / 3 已撤销 */
    private Integer status;
    private String approveNote;
    private LocalDateTime createdAt;
}
