package com.laimengchao.sso.settings.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 审批页面配置（客户端侧映射）。
 * <p>
 * {@code config} 是字段与布局的 JSON 原文，业务前端按 renderMode 自行渲染：
 * SECTIONS（分组卡片）/ ROUTE（自定义页面），页面没配置时退回内置默认版式。
 * ROUTE 不逐字段配置，{@code routePath} 挂一个自定义页面地址，前端抽屉内 iframe 嵌入。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApprovalPageDTO {

    private String view;
    private String renderMode;
    private String config;
    /** renderMode=ROUTE 的自定义页面地址（应用内路由或完整 URL） */
    private String routePath;
    private Integer version;
}
