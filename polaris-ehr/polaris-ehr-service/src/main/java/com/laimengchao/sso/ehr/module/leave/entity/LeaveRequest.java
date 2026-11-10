package com.laimengchao.sso.ehr.module.leave.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 请假申请。申请人记 account_id（网关可信头）与员工档案双份，前者用于「我的」，后者用于统计 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_leave")
public class LeaveRequest extends BaseEntity {

    /** 请假单号：由配置中心单号规则生成 */
    private String leaveNo;
    /** 开始时间 HH:mm（精确到半小时） */
    private String startTime;
    /** 结束时间 HH:mm（精确到半小时） */
    private String endTime;
    private Long employeeId;
    private Long accountId;
    private String applicantName;
    /** ANNUAL/SICK/PERSONAL/MARRIAGE/MATERNITY/OTHER */
    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    /** 开始半天：AM 上午 / PM 下午 */
    private String startPeriod;
    /** 结束半天：AM 上午 / PM 下午 */
    private String endPeriod;
    /** 计算模式：NATURAL 自然日 / WORKDAY 工作日 */
    private String dayMode;
    private BigDecimal days;
    private String reason;
    /** 证明材料附件ID：病假/婚假/产假等规则要求必传 */
    private Long attachmentId;
    /** 实际返岗日期：销假后写入，status 变为 4 */
    private LocalDate returnDate;
    /** 审批转办人账户ID：部门负责人请假时必填，审批通过后按请假期间登记审批委托 */
    private Long delegateAccountId;
    private String delegateName;
    /** 0 待审批 / 1 已通过 / 2 已驳回 / 3 已撤销 / 4 已销假 */
    private Integer status;
    private Long approverAccountId;
    private String approverName;
    private String approveNote;
    private LocalDateTime approvedAt;

    /** 审批中心实例 ID：统一走审批中心后，撤销要据此撤回在途单、列表要据此点开详情 */
    private Long approvalInstanceId;
}
