package com.laimengchao.sso.ehr.module.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 部门负责人变更记录：每一次「设置 / 更换 / 清除」负责人各留一行。
 * <p>
 * 这张表不只是流水，它同时是<b>变更审批的业务单据</b>：发起审批时 bizId 就是本行 id，
 * 终审回调按它回填状态与生效时间。因此记录行里的组织名、原负责人、新负责人
 * 全部是提交那一刻的<b>快照</b>——员工改名、部门改路径、组织被删，历史记录仍然读得懂。
 * <p>
 * {@code changeType} 区分三种语义：
 * <ul>
 *   <li>{@code SET} —— 首次设置（原来没有负责人），同样走审批；</li>
 *   <li>{@code CHANGE} —— 更换负责人；</li>
 *   <li>{@code CLEAR} —— 清除负责人，属纠错动作，直接生效（status 直接为 1）。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_leader_change")
public class LeaderChange extends BaseEntity {

    /** 首次设置：原来没有负责人 */
    public static final String TYPE_SET = "SET";
    /** 更换：原来有负责人，换成别人 */
    public static final String TYPE_CHANGE = "CHANGE";
    /** 清除负责人（直接生效，不发起审批） */
    public static final String TYPE_CLEAR = "CLEAR";

    /** 0 审批中 / 1 已生效 / 2 已驳回 / 3 已撤销 */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_EFFECTIVE = 1;
    public static final int STATUS_REJECTED = 2;
    public static final int STATUS_CANCELLED = 3;

    /** 组织节点ID */
    private Long orgId;
    /** 组织名称快照 */
    private String orgName;
    /** 组织全路径快照，如「晨星科技集团 / 西安子公司 / 研发部」 */
    private String orgPath;
    /** 组织类型快照 GROUP/COMPANY/DEPARTMENT/PROJECT/SUB_PROJECT */
    private String orgType;

    /** SET 首次设置 / CHANGE 更换 / CLEAR 清除 */
    private String changeType;

    /** 原负责人员工ID；首次设置为空 */
    private Long oldLeaderId;
    /** 原负责人姓名快照；首次设置为空 */
    private String oldLeaderName;
    /** 新负责人员工ID；清除时为空 */
    private Long newLeaderId;
    /** 新负责人姓名快照；清除时为空 */
    private String newLeaderName;
    /** 新负责人工号快照 */
    private String newLeaderEmpNo;
    /** 新负责人职位快照 */
    private String newLeaderPosition;

    /** 变更原因（必填：负责人变更必须有据可查） */
    private String reason;
    /** 0 审批中 / 1 已生效 / 2 已驳回 / 3 已撤销 */
    private Integer status;

    /** 发起人账号ID + 姓名快照 */
    private Long applicantAccountId;
    private String applicantName;
    /** 审批中心实例ID：发起即终态时为空（不会把已结束的单号挂回来） */
    private Long approvalInstanceId;
    /** 生效时间：审批通过或直接生效的时刻 */
    private LocalDateTime effectiveAt;
    /** 备注：驳回原因、发起失败说明 */
    private String remark;
}
