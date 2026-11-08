package com.laimengchao.sso.settings.common;

import com.laimengchao.sso.common.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Settings 业务错误码段（通用段见 common 的 ErrorCode；本枚举只含 SETTINGS 业务码）。
 * 实现 IErrorCode 即可接入统一的 R / BizException / GlobalExceptionHandler。
 */
@Getter
@AllArgsConstructor
public enum SettingsErrorCode implements IErrorCode {

    DICT_TYPE_NOT_FOUND(8001, "字典类型不存在"),
    DICT_TYPE_DUPLICATED(8002, "字典类型编码已存在"),
    DICT_TYPE_BUILT_IN(8003, "内置字典类型不允许该操作"),
    DICT_TYPE_IN_USE(8004, "该字典类型下还有字典项，不能删除"),
    DICT_ITEM_NOT_FOUND(8005, "字典项不存在"),
    DICT_ITEM_DUPLICATED(8006, "该类型下已存在相同取值的字典项"),
    DICT_ITEM_BUILT_IN(8007, "内置字典项不允许删除"),
    DICT_CODE_INVALID(8008, "字典编码不合法（小写字母开头，仅小写字母/数字/点/下划线/中划线）"),
    BIZ_TYPE_UNKNOWN(8101, "未知的主数据类别"),
    BIZ_ITEM_NOT_FOUND(8102, "主数据项不存在"),
    BIZ_ITEM_DUPLICATED(8103, "该类别下已存在相同取值的数据项"),
    BIZ_ITEM_BUILT_IN(8104, "内置数据项不允许删除"),

    /* 审批中心 */
    APPROVAL_TEMPLATE_NOT_FOUND(8201, "该业务场景未配置审批模板"),
    APPROVAL_TEMPLATE_DISABLED(8202, "审批模板已停用"),
    APPROVAL_INSTANCE_NOT_FOUND(8203, "审批单不存在"),
    APPROVAL_TASK_NOT_FOUND(8204, "审批任务不存在"),
    APPROVAL_TASK_NOT_MINE(8205, "这条审批任务不属于你"),
    APPROVAL_TASK_DONE(8206, "该任务已处理，请刷新"),
    APPROVAL_INSTANCE_RUNNING(8207, "该业务已有在途审批单"),
    APPROVAL_INSTANCE_FINISHED(8208, "审批单已结束，无法操作"),
    APPROVAL_APPROVER_EMPTY(8209, "该步骤未解析到审批人，流程无法发起"),
    APPROVAL_CONDITION_INVALID(8210, "条件表达式不合法"),
    APPROVAL_STEP_INVALID(8211, "流程步骤配置不合法"),
    APPROVAL_NOT_APPLICANT(8212, "只有发起人可以撤回"),
    APPROVAL_TRANSFER_LIMIT(8213, "该任务转办次数已达上限"),
    APPROVAL_SCENE_NOT_FOUND(8214, "业务场景未注册"),
    APPROVAL_PAGE_NOT_FOUND(8215, "页面配置不存在"),
    APPROVAL_TEMPLATE_IN_USE(8216, "该模板还有在途单，不能删除"),
    APPROVAL_BIZ_POINT_IN_USE(8217, "业务点已被引用，不能删除");

    private final int code;
    private final String message;
}
