package com.laimengchao.sso.ehr.module.approval.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/** 复职申请视图 */
@Data
public class RehireApplyVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;
    private String empNo;
    private String employeeName;
    private String orgName;
    /** 原离职日期 */
    private String resignDate;
    /** 拟复职日期 */
    private String rehireDate;
    /** 离职月数：审批人关心的「断了多久」 */
    private Long leftMonths;
    private String reason;
    private Boolean enableAccount;
    private String accountAction;
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
