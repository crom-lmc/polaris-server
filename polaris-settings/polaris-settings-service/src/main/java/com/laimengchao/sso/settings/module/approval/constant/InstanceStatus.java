package com.laimengchao.sso.settings.module.approval.constant;

/**
 * 审批实例状态。
 * <p>
 * 只有 RUNNING 是「在途」；进入其它四态即终态，终态会触发业务回调（见 ApprovalCallbackService）。
 */
public enum InstanceStatus {

    RUNNING(0, "进行中"),
    APPROVED(1, "已通过"),
    REJECTED(2, "已驳回"),
    WITHDRAWN(3, "已撤回"),
    TERMINATED(4, "已终止");

    private final int code;
    private final String label;

    InstanceStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public Integer getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static InstanceStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (InstanceStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }

    /** 是否终态 */
    public boolean isFinal() {
        return this != RUNNING;
    }
}
