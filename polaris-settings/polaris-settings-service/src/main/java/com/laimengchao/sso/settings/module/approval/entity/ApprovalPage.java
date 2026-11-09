package com.laimengchao.sso.settings.module.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批页面配置。一个模板可挂多套视图（审批人 / 发起人 / 审计）。
 * <p>
 * renderMode 决定渲染方式：GRID / SECTIONS / TEMPLATE / COMPONENT / ROUTE，
 * 四级降级——TEMPLATE 编译失败退 SECTIONS，SECTIONS 没配退 GRID，再退内置；
 * ROUTE 不逐字段配置，直接挂一个自定义页面地址，前端抽屉内 iframe 嵌入。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cfg_approval_page")
public class ApprovalPage extends BaseEntity {

    private Long templateId;
    /** APPROVAL 审批人 / DETAIL 发起人回看 / AUDIT 审计 */
    private String view;
    /** SECTIONS / ROUTE */
    private String renderMode;
    /** 字段与布局 JSON */
    private String config;
    /** renderMode=ROUTE 的自定义页面地址（应用内路由或完整 URL） */
    private String routePath;
    private Integer version;
}
