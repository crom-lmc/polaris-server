package com.laimengchao.sso.ehr.module.org.entity;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 组织架构节点类型与「谁能挂在谁下面」的唯一真相源。
 * <p>
 * 规则（前后端共用同一口径，前端只做提示，后端强校验）：
 * <pre>
 *   集团 GROUP        → 子公司 COMPANY / 部门 DEPARTMENT / 项目 PROJECT
 *   子公司 COMPANY    → 部门 DEPARTMENT / 项目 PROJECT      （不能再挂集团，也不能再挂子公司）
 *   部门 DEPARTMENT   → 部门 DEPARTMENT / 项目 PROJECT      （部门可以再分部门，如「研发中心/运维部」）
 *   项目 PROJECT      → 子项目 SUB_PROJECT                 （不能再挂集团/子公司/部门）
 *   子项目 SUB_PROJECT → 叶子，不能再挂任何节点
 * </pre>
 * 根节点只能是集团（没有上级时 type 必须为 GROUP）。
 */
@Getter
public enum OrgType {

    GROUP("集团"),
    COMPANY("子公司"),
    DEPARTMENT("部门"),
    PROJECT("项目"),
    SUB_PROJECT("子项目");

    private final String label;

    OrgType(String label) {
        this.label = label;
    }

    /** 允许挂在当前类型下面的子类型；空集合代表叶子节点 */
    public Set<OrgType> allowedChildren() {
        return switch (this) {
            case GROUP -> Set.of(COMPANY, DEPARTMENT, PROJECT);
            case COMPANY -> Set.of(DEPARTMENT, PROJECT);
            case DEPARTMENT -> Set.of(DEPARTMENT, PROJECT);
            case PROJECT -> Set.of(SUB_PROJECT);
            case SUB_PROJECT -> Set.of();
        };
    }

    public boolean canBeRoot() {
        return this == GROUP;
    }

    public static OrgType of(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 前端下拉用：所有类型 + 中文名 */
    public static List<OrgType> all() {
        return Arrays.asList(values());
    }
}
