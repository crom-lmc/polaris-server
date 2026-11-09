package com.laimengchao.sso.settings.module.approval.constant;

/**
 * 审批流水动作。全部动作都落 appr_action，用于追溯「谁在什么时候动了什么」。
 */
public enum ActionType {

    /** 发起（业务方提交） */
    SUBMIT,
    APPROVE,
    REJECT,
    /** 无人可审自动跳过 */
    SKIP,
    /** 转办 */
    TRANSFER,
    /** 加签 */
    ADD_SIGN,
    /** 抄送 */
    CC,
    /** 发起人撤回 */
    WITHDRAW,
    /** 管理员终止 */
    TERMINATE,
    /** 运营催办（给当前待办审批人发提醒） */
    URGE,
    /** SLA 超时定时提醒（系统动作，actor 为空） */
    SLA_REMIND
}
