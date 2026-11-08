package com.laimengchao.sso.settings.module.approval.constant;

/**
 * 审批任务（每人每条）状态。
 * <p>
 * 待办口径只有 PENDING —— 「待我审批」直接按 (approver_id, status=0) 查。
 * CANCELLED 用于或签竞争失败、加签撤回等场景，保留记录但不再可操作。
 */
public enum TaskStatus {

    PENDING(0, "待审批"),
    APPROVED(1, "已通过"),
    REJECTED(2, "已驳回"),
    SKIPPED(3, "已跳过"),
    CANCELLED(4, "已作废"),
    TRANSFERRED(9, "已转办");

    private final int code;
    private final String label;

    TaskStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public Integer getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static TaskStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (TaskStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }

    /** 是否已处理完，非待办 */
    public boolean isDone() {
        return this != PENDING;
    }
}
