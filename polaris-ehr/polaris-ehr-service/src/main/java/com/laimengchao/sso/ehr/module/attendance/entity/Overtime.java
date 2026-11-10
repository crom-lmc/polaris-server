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
    /** 加班单号：由配置中心单号规则生成 */
    private String overtimeNo;
    private Long employeeId;
    private Long accountId;
    private LocalDate workDate;
    private BigDecimal hours;
    /** NORMAL 工作日 / HOLIDAY 节假日 / RESTDAY 休息日 */
    private String type;
    private String reason;
    /** 0 待审批 / 1 已通过 / 2 已驳回 / 3 已撤销 */
    private Integer status;
    /** 调休结算：0 未结算 / 1 已结算 */
    private Integer settleStatus;
    /** 审批通过后计入调休的有效小时数 */
    private BigDecimal effectiveHours;
    /** 结算为调休额度的时间 */
    private LocalDateTime settledAt;
    /** 核算快照：1:1 转调休 */
    private String calcDetail;
    private String approveNote;
    private LocalDateTime createdAt;
}
