package com.laimengchao.sso.ehr.module.approval.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 审批中心的统一待办条目：请假与离职/减员共用一张表渲染，
 * {@code type} 决定前端调哪个审批接口（LEAVE / RESIGN_SELF / RESIGN_HR）。
 */
@Data
public class ApprovalItemVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** LEAVE 请假 / RESIGN_SELF 离职申请 / RESIGN_HR 减员 */
    private String type;
    private String typeName;
    private String applicantName;
    private String orgName;
    /** 一句话摘要，例如「年假 3 天」「离职申请」 */
    private String title;
    /** 业务日期区间或拟离职日期 */
    private String period;
    private String reason;
    private Integer status;
    private String statusName;
    /** 当前卡在哪个审批环节，例如「部门负责人」「集团人力资源中心」；请假单为空 */
    private String stageName;
    private String createdAt;
    private String approverName;
    private String approveNote;
    private String approvedAt;
    /** 当前登录人是否可以审批这一条 */
    private boolean actionable;

    /* ---------- 离职/减员单的交接进度（请假单为空） ---------- */

    private String handoverName;
    private String handoverProgress;
    /** 交接是否已完成（含「无需交接」）；false 时前端禁用「通过」 */
    private boolean handoverCompleted;
    private int handoverDone;
    private int handoverTotal;
    /** 当前登录人是否就是这条的交接人 */
    private boolean handoverMine;
}
