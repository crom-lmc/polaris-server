package com.laimengchao.sso.ehr.module.org.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 设置 / 更换节点负责人入参。
 * <p>
 * {@code leaderEmployeeId} 传 0 或 null 表示清除负责人（清空要显式下传，
 * 否则后端会当成"不修改"）。
 * <p>
 * {@code reason} 是<b>变更原因</b>，指定新负责人时必填（后端强校验，前端只是提前拦一下）：
 * 负责人决定审批链路挂到谁身上，换人必须留得下原因，所以它同时写进
 * {@code ehr_leader_change.reason} 与审批表单快照，审批人和事后审计都看得到。
 */
@Data
public class OrgLeaderRequest {

    /** 员工档案 ID */
    private Long leaderEmployeeId;

    /** 变更原因：设置/更换负责人时必填，清除时可空 */
    @Size(max = 500, message = "变更原因最多 500 字")
    private String reason;
}
