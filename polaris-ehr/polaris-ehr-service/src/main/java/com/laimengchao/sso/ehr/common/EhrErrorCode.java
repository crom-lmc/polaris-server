package com.laimengchao.sso.ehr.common;

import com.laimengchao.sso.common.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Ehr 业务错误码段（通用段见 common 的 ErrorCode；本枚举只含 EHR 业务码）。
 * 实现 IErrorCode 即可接入统一的 R / BizException / GlobalExceptionHandler。
 */
@Getter
@AllArgsConstructor
public enum EhrErrorCode implements IErrorCode {

    ORG_TYPE_INVALID(7001, "组织类型不合法"),
    ORG_PARENT_NOT_ALLOWED(7002, "该类型的上级节点不允许挂这个类型"),
    ORG_HAS_CHILDREN(7003, "该节点下还有子节点，不能删除"),
    ORG_HAS_EMPLOYEES(7004, "该节点下还有员工，不能删除"),
    ORG_NAME_DUPLICATED(7005, "同级下已存在同名节点"),
    ORG_CYCLE(7006, "不能把节点移动到自身或自己的下级"),
    EMP_NO_DUPLICATED(7007, "工号已存在"),
    EMP_NOT_FOUND(7008, "员工不存在"),
    EMP_ACCOUNT_EXISTS(7009, "该员工已绑定账号"),
    ACCOUNT_OPEN_FAILED(7010, "账号开通失败"),
    LEAVE_DATE_INVALID(7011, "请假日期不合法"),
    LEAVE_STATE_INVALID(7012, "当前状态不允许该操作"),
    LEAVE_NOT_APPLICANT(7013, "只能操作自己的请假申请"),
    EMP_STATUS_INVALID(7014, "员工当前状态不允许该操作"),
    ACCOUNT_STATUS_FAILED(7015, "账号状态同步失败"),
    APPROVAL_SELF(7016, "不能审批自己提交的申请"),
    APPROVAL_NOT_APPROVER(7017, "该申请不由你审批"),
    APPROVAL_STATE_INVALID(7018, "申请当前状态不允许该操作"),
    APPROVAL_NOT_APPLICANT(7019, "只能操作自己提交的申请"),
    APPROVAL_DUPLICATED(7020, "已有待审批的申请，请先处理"),
    HANDOVER_INCOMPLETE(7021, "交接项尚未全部确认，还不能进入审批"),
    HANDOVER_CONFIRMER_INVALID(7022, "交接人不合法（需在职、已开户、且不是本人）"),
    HANDOVER_ITEM_INVALID(7023, "交接项目不合法"),
    HANDOVER_NOT_CONFIRMER(7024, "你不是本单的交接人，无权确认"),
    EMP_ID_CARD_INVALID(7025, "身份证号不合法"),
    EMP_ID_CARD_DUPLICATED(7026, "身份证号已存在于其他员工档案"),
    EMP_PROFILE_INVALID(7027, "人员子档案信息不合法"),
    EMP_MOBILE_DUPLICATED(7028, "手机号已存在于其他员工档案"),
    EMP_DELETE_NOT_ALLOWED(7029, "只有「待入职」草稿可以删除"),
    EMP_EDIT_NOT_ALLOWED(7030, "当前状态不允许变更员工信息"),
    EMP_ONBOARD_INCOMPLETE(7031, "入职资料未填完整"),
    ORG_LEADER_INVALID(7032, "负责人必须是试用期或在职员工"),
    ORG_FIELD_LOCKED(7033, "该字段创建后不可修改"),
    ORG_LEADER_NOT_IN_NODE(7034, "负责人必须是本部门员工"),
    /** 上级组织编码不是规范的 XC 层级编码，不能生成子编码 */
    ORG_CODE_INVALID(7037, "组织编码不合法"),
    /** 同级 4 位序号（0001~9999）已用尽 */
    ORG_CODE_EXHAUSTED(7038, "组织编码序号已用尽"),
    /** 向 settings 审批中心发起审批失败（不可用 / 被拒 / 无在途单） */
    APPROVAL_START_FAILED(7035, "发起审批失败"),
    /** 审批单不存在、已结束，或当前步骤不由你办理 */
    APPROVAL_INSTANCE_MISSING(7036, "审批单不存在或已结束"),
    /** 同一部门已有负责人变更审批在途：一次只允许一单，避免两个新负责人先后落库 */
    ORG_LEADER_APPROVING(7039, "该部门已有负责人变更审批在进行中，请等审批结束后再操作"),
    /** 假期额度不足（并发冻结的条件更新影响行数为 0） */
    LEAVE_QUOTA_NOT_ENOUGH(7040, "假期余额不足"),
    /** 请假类型规则不允许本次申请（未启用 / 天数越界 / 提前申请不足） */
    LEAVE_RULE_INVALID(7041, "请假类型规则不允许该申请"),
    /** 与已有待审批 / 已通过的请假单日期重叠 */
    LEAVE_DATE_OVERLAP(7042, "请假时间与已有请假单重叠"),
    /** 该类型必须上传证明材料 */
    LEAVE_ATTACHMENT_REQUIRED(7043, "该请假类型必须上传证明材料"),
    /** 请假天数不在类型规则允许区间 */
    LEAVE_LEAVE_DAYS_INVALID(7044, "请假天数不符合类型规则"),
    /** 只有已通过的请假单可以销假，且不可重复销假 */
    LEAVE_EARLY_RETURN_INVALID(7045, "当前请假单不可销假"),
    /** 审批转办人不合法（本人 / 离职 / 重复） */
    LEAVE_DELEGATE_INVALID(7046, "审批转办人不合法"),
    /** 假期额度记录不存在 */
    LEAVE_QUOTA_NOT_FOUND(7047, "假期额度记录不存在"),
    /** 附件不存在或不属于当前业务单据 */
    ATTACHMENT_NOT_FOUND(7048, "附件不存在");

    private final int code;
    private final String message;
}
