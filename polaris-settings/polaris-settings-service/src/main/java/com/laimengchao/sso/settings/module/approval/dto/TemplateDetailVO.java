package com.laimengchao.sso.settings.module.approval.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 模板详情：模板本体 + 步骤 + 分支 + 页面配置，编辑器一次拿全。
 */
@Data
public class TemplateDetailVO {

    private Long id;
    private String code;
    private String name;
    private String appCode;
    private String bizType;
    private String bizName;
    private Integer enabled;
    private Integer version;
    private String remark;

    /** 列表页用：步骤数与分支数，避免为拿计数把整棵树都返出去 */
    private Integer stepCount;
    private Integer nodeCount;
    /** 当前是否还有在途单（有则禁止删除） */
    private Integer runningCount;

    private List<StepVO> steps = new ArrayList<>();
    private PageVO page;

    @Data
    public static class StepVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private Integer seq;
        private String name;
        private String conditionExpr;
        private String conditionDesc;
        private String rejectAction;
        /** 所属分支节点 ID（真实雪花，字符串返回）；NULL=主干 */
        @JsonSerialize(using = ToStringSerializer.class)
        private Long parentNodeId;
        private String branchPath;
        private Integer depth;
        private String uiKind;
        /** 该步骤是否为条件网关（多分支且带条件/兜底）；前端据此还原树 */
        private Boolean isGateway;
        private List<NodeVO> nodes = new ArrayList<>();
    }

    @Data
    public static class NodeVO {
        /** 真实雪花 ID，字符串返回，前端用它把扁平 steps 还原成树 */
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private Integer seq;
        private String name;
        private String conditionExpr;
        private String conditionDesc;
        private String approvers;
        private String approveMode;
        private Integer allowSelf;
        private Integer skipSame;
        private String onEmpty;
        private Integer timeoutHours;
        private Integer isFallback;
        private Integer childChain;
    }

    @Data
    public static class PageVO {
        private String view;
        private String renderMode;
        private String config;
        private String htmlTpl;
        private String componentKey;
        /** renderMode=ROUTE 的自定义页面地址 */
        private String routePath;
        private Integer version;
    }
}
