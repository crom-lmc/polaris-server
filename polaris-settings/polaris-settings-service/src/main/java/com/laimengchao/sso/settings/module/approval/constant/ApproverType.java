package com.laimengchao.sso.settings.module.approval.constant;

/**
 * 审批人规则类型。
 * <p>
 * 一个分支可以挂多条规则，运行时取并集去重（例如「组织负责人 + 角色 HRBP」）。
 * <ul>
 *   <li>ORG_LEADER：组织负责人，可带 levelOffset 向上偏移（0 本级 / 1 上一级）</li>
 *   <li>ORG_LEADER_BY_NAME：按组织名称取负责人（职能线审批，如「人力资源中心」）</li>
 *   <li>ROLE：指定角色下的所有账号</li>
 *   <li>USER：指定账号</li>
 *   <li>APPLICANT：发起人本人（用于「本人确认」这类节点）</li>
 * </ul>
 * 注意：<b>没有「权限点」类型</b>。历史上曾支持 PERM（按权限码反查持有人在审批），
 * 但权限是「能不能做某个动作」，与「这个单该谁审」不是一回事：一个人可能同时持多个
 * 应用的权限码，反查没有应用维度会串审批人。现已整体下架，存量配置迁移为「角色/指定人」。
 */
public enum ApproverType {

    ORG_LEADER("组织负责人"),
    ORG_LEADER_BY_NAME("指定组织负责人"),
    ROLE("角色"),
    USER("指定人"),
    APPLICANT("发起人");

    private final String label;

    ApproverType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ApproverType of(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return valueOf(v.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
