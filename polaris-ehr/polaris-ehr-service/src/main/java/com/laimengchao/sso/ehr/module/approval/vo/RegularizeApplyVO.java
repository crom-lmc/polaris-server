package com.laimengchao.sso.ehr.module.approval.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/** 转正申请视图 */
@Data
public class RegularizeApplyVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;
    private String empNo;
    /** 申请单号 */
    private String applyNo;
    private String employeeName;
    private String orgName;
    private String orgPath;
    private String position;
    /** 入职日期 */
    private String hireDate;
    /** 试用期月数 */
    private Integer probationMonths;
    /** 预计转正日期 */
    private String expectedRegularizeDate;
    /** 申请转正日期 */
    private String regularizeDate;
    private String selfReview;
    private String workSummary;
    private String reason;
    private String nextPlan;
    private Integer status;
    private String statusName;
    private String applicantName;
    private String createdAt;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long approvalInstanceId;
    private String approvalBizType;
    /** 是否本人提交 */
    private boolean mine;
}
