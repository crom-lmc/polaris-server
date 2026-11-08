package com.laimengchao.sso.settings.module.approval.constant;

/**
 * 驳回后的走向。
 * <ul>
 *   <li>END：整单驳回，退回发起人修改后重新发起（默认，与现有 EHR 行为一致）</li>
 *   <li>PREV：退回上一审批步骤重走</li>
 *   <li>NODE：仅当前步骤重走（同步骤其他分支保持已过状态）</li>
 * </ul>
 */
public enum RejectAction {

    END,
    PREV,
    NODE;

    public static RejectAction of(String v) {
        if (v == null || v.isBlank()) {
            return END;
        }
        try {
            return valueOf(v.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return END;
        }
    }
}
