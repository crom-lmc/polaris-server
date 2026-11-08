package com.laimengchao.sso.settings.module.approval.constant;

/**
 * 页面视图。同一模板可以为不同角色配不同页面。
 */
public enum ViewType {

    /** 审批人看到的（默认） */
    APPROVAL,
    /** 发起人回看自己的单据 */
    DETAIL,
    /** 审计只读 */
    AUDIT;

    public static ViewType of(String v) {
        if (v == null || v.isBlank()) {
            return APPROVAL;
        }
        try {
            return valueOf(v.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return APPROVAL;
        }
    }
}
