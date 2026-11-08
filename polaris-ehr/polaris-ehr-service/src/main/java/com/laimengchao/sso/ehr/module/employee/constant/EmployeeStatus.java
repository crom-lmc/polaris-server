package com.laimengchao.sso.ehr.module.employee.constant;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 员工生命周期状态。取代原先只有「在职 / 离职」两态的写法，覆盖完整入职过程：
 * <pre>
 *   10 待入职（草稿，可编辑，提交后进入审批）
 *   20 入职审批中（用人部门 → 人事主管 两级审批）
 *   25 入职审批通过（审批已通过、账号已开通，待 HR 在列表办理「入职」）
 *   30 试用期（办理入职后进入，期间可提转正申请）
 *   40 在职（正式，转正审批通过 / 免试用期入职直转）
 *   50 离职中（离职/减员申请审批 + 交接期间）
 *   55 待关闭账号（离职/减员审批通过，等 HR 在人员列表点「关闭账号」）
 *   60 已离职（账号已关闭、正式离岗）
 * </pre>
 * 历史数据迁移：旧 1（在职）→ 40，旧 2（离职）→ 60。
 */
public enum EmployeeStatus {

    PENDING(10, "待入职"),
    ONBOARDING(20, "入职审批中"),
    ONBOARD_APPROVED(25, "入职审批通过"),
    PROBATION(30, "试用期"),
    ACTIVE(40, "在职"),
    OFFBOARDING(50, "离职中"),
    /** 审批已通过、但账号还没关：列表只允许「查看」和「关闭账号」 */
    PENDING_ACCOUNT_CLOSE(55, "待关闭账号"),
    LEFT(60, "已离职");

    private final int code;
    private final String label;

    EmployeeStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 写入 JSON / 落库用数字编码 */
    @JsonValue
    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 正式用工（试用期 + 在职），可被减员、可提交请假 */
    public boolean isWorking() {
        return this == PROBATION || this == ACTIVE;
    }

    /** 已归档（离职），不可再被减员 / 提交申请 */
    public boolean isLeft() {
        return this == LEFT;
    }

    public static EmployeeStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (EmployeeStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
