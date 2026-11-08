package com.laimengchao.sso.settings.module.approval.constant;

/**
 * 审批页渲染模式，四级降级链：TEMPLATE → COMPONENT → SECTIONS → GRID → 内置。
 */
public enum RenderMode {

    /** 单区栅格，最简单 */
    GRID,
    /** 分组卡片（默认） */
    SECTIONS,
    /** 受限 HTML 模板 */
    TEMPLATE,
    /** 前端注册组件 */
    COMPONENT;

    public static RenderMode of(String v) {
        if (v == null || v.isBlank()) {
            return SECTIONS;
        }
        try {
            return valueOf(v.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SECTIONS;
        }
    }
}
