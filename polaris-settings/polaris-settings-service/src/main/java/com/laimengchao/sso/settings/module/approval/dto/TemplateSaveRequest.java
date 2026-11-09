package com.laimengchao.sso.settings.module.approval.dto;

import com.laimengchao.sso.settings.module.approval.model.ApprovalSnapshot.ApproverRule;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 模板保存请求：整体替换式保存（步骤、分支、页面一起提交）。
 * <p>
 * v5：前端按 <b>DFS 前序</b> 把树展平成一维 steps[]，用 parentNodeRef 指向「父网关里某个分支的
 * 前端本地 clientId」。后端先给所有 NodeDto 预分配雪花 ID，再把 parentNodeRef 换成真实 ID、
 * 重算 branchPath / depth / seq —— 前端的本地 id 一律不落库。
 * <p>
 * 不做增量 diff —— 编辑器里改一个分支的审批人，前端提交的是整棵树，
 * 后端按 template_id 删旧插新最简单，也不会出现「改了没生效」的诡异状态。
 */
@Data
public class TemplateSaveRequest {

    private Long id;
    private String code;
    private String name;
    private String appCode;
    private String bizType;
    private String bizName;
    private Integer enabled;
    private String remark;

    private List<StepDto> steps = new ArrayList<>();

    /** 审批页面配置，随模板一起保存；为空则不动已有配置 */
    private PageDto page;

    @Data
    public static class StepDto {
        /** 前端给的顺序号，后端按数组顺序重排为 1..n，不信任该值 */
        private Integer seq;
        /** 前端本地步骤 id，仅用于排查，不落库 */
        private String clientKey;
        /** 父网关里某个分支 node 的 clientId；NULL/空 = 主干或普通步骤 */
        private String parentNodeRef;
        /** 前端算出的路径，后端重算，仅作对照 */
        private String branchPath;
        /** 前端算出的深度，后端重算并校验 <= 3 */
        private Integer depth;
        /** APPROVER / GATEWAY / PARALLEL / END，仅编辑器提示 */
        private String uiKind;
        private String name;
        private String conditionExpr;
        private String conditionDesc;
        /** v5 起固定 END（整单终止），保留字段以兼容旧报文 */
        private String rejectAction;
        /** 时效小时数：该步骤停留超过此值触发超时提醒，空=不提醒 */
        private Integer slaHours;
        private List<NodeDto> nodes = new ArrayList<>();
    }

    @Data
    public static class NodeDto {
        /** 前端本地 id；保存时映射成雪花 ID，网关分支卡靠它被子步骤引用 */
        private String clientId;
        private Integer seq;
        private String name;
        private String conditionExpr;
        private String conditionDesc;
        /** 兜底分支：其它分支均未命中时才生效 */
        private Integer isFallback;
        /** 该分支是否挂了子链（冗余标记） */
        private Integer childChain;
        private List<ApproverRule> approvers = new ArrayList<>();
        private String approveMode;
        private Integer allowSelf;
        private Integer skipSame;
        private String onEmpty;
        private Integer timeoutHours;
    }

    @Data
    public static class PageDto {
        private String view;
        private String renderMode;
        private String config;
        /** renderMode=ROUTE 的自定义页面地址 */
        private String routePath;
    }
}
