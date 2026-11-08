package com.laimengchao.sso.settings.module.approval.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 流程快照。实例发起时从模板复制一份冻结，之后引擎只读快照，不再回查模板。
 * <p>
 * 这是「改模板不影响在途单」的实现基础：模板版本升到 6，在途单仍按它发起时的结构跑。
 * <p>
 * 注意快照里存的是<b>审批人规则</b>而不是解析后的人。规则到步骤激活时才解析，
 * 这样「组织负责人」在审批过程中换人，后续步骤能反映最新状态。
 * <p>
 * <b>v5 树形结构</b>：steps 仍是扁平数组，但按 DFS 前序编号；每个 Step 带
 * branchPath / parentNodeId / depth，描述它属于哪条条件分支。引擎按路径寻址，
 * 用「全实例 PENDING=0 + 沿 branchPath 逐级回退」实现任意深度（<=3）的分层汇合。
 * 旧快照缺这三个字段时 path()/depth() 归一化为主干，行为与线性流程一致。
 */
@Data
public class ApprovalSnapshot {

    private Integer version = 1;
    private List<Step> steps = new ArrayList<>();

    @Data
    public static class Step {
        private Integer seq;
        private String name;
        /** 步骤级条件，空=总执行 */
        private String conditionExpr = "";
        /** v5 起固定 END（整单终止），保留字段以兼容旧快照 */
        private String rejectAction = "END";
        /** 路径键：主干 ''；分支子链 'n/&lt;nodeId&gt;'，多段用 / 连接 */
        private String branchPath = "";
        /** 所属分支节点 ID；NULL=主干 */
        private Long parentNodeId;
        /** 嵌套深度，主干 0 */
        private Integer depth = 0;
        /** 编辑器提示，引擎不读 */
        private String uiKind = "APPROVER";
        private List<Node> nodes = new ArrayList<>();

        /** 旧快照缺字段时归一化为主干 */
        @JsonIgnore
        public String path() {
            return branchPath == null ? "" : branchPath;
        }

        @JsonIgnore
        public int depth() {
            return depth == null ? 0 : depth;
        }

        /** 条件网关 = 多分支且至少一条带条件或兜底标记；并行会签（多分支无条件）不算网关 */
        @JsonIgnore
        public boolean isGateway() {
            if (nodes == null || nodes.size() <= 1) {
                return false;
            }
            for (Node n : nodes) {
                if (Boolean.TRUE.equals(n.getIsFallback())
                        || (n.getConditionExpr() != null && !n.getConditionExpr().isBlank())) {
                    return true;
                }
            }
            return false;
        }
    }

    @Data
    public static class Node {
        private Long nodeId;
        private Integer seq;
        private String name = "";
        /** 分支条件，空=总生效 */
        private String conditionExpr = "";
        /** 兜底分支：同一网关其它分支均未命中时才生效 */
        private Boolean isFallback = false;
        /** ANY / ALL / SEQ */
        private String approveMode = "ANY";
        private Integer allowSelf = 0;
        private Integer skipSame = 1;
        /** SKIP / BLOCK */
        private String onEmpty = "SKIP";
        private List<ApproverRule> approvers = new ArrayList<>();
    }

    /**
     * 审批人规则。不同类型用的字段不同：
     * USER 用 userId；ROLE / ORG_LEADER_BY_NAME 用 code（后者存组织名称）；ORG_LEADER 用 source / levelOffset / toRoot。
     * <p>没有「权限点」类型——按权限码反查持有人会把「能做什么」和「该谁审」混为一谈。
     * <p>ORG_LEADER 的<b>取谁的组织</b>原本写死为申请人组织（applicantOrgId）。
     * 现在可配置化：source 为空 = 沿用申请人组织（存量模板零影响）；
     * source = {@code FIELD:<业务点字段code>:<USER|ORG>} 表示从审批表单（冻结快照 formData）里取该字段的值当组织来源——
     * ORG 型字段值即组织 ID；USER 型字段值即人员（账号）ID，由 EHR 反查其所属组织。
     * <p>toRoot=true 时忽略 levelOffset，直接上溯到集团（根的负责人）取「这一个人」，用于「审批到集团」场景。
     */
    @Data
    public static class ApproverRule {
        /** ORG_LEADER / ORG_LEADER_BY_NAME / ROLE / USER / APPLICANT */
        private String type;
        /** USER 型：账号 ID。用字符串承载——雪花 ID 在 JS 侧会丢精度，前端原样传回 */
        private String userId;
        private String code;
        private Integer levelOffset = 0;
        /**
         * ORG_LEADER 取组织来源。null/空 = 申请人组织（APPLICANT，原行为）；
         * {@code FIELD:<code>:<USER|ORG>} = 从业务表单字段取值当来源。
         */
        private String source;
        /** ORG_LEADER：true=直接审批到集团负责人（单审批人），忽略 levelOffset */
        private Boolean toRoot = false;
    }

    /** 解析出的实际审批人 */
    @Data
    public static class ApproverRef {
        private Long id;
        private String name;

        public ApproverRef() {
        }

        public ApproverRef(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    // ================================================================ 路径工具

    /** 子链路径 = 父路径 + 一段分支 */
    public static String childPath(String parentPath, Long nodeId) {
        String p = parentPath == null ? "" : parentPath;
        return p.isEmpty() ? "n/" + nodeId : p + "/n/" + nodeId;
    }

    /** 去掉最后一段，回到父路径；主干返回空串 */
    public static String parentPath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int i = path.lastIndexOf("/n/");
        return i <= 0 ? "" : path.substring(0, i);
    }

    /** 取出路径最后一段的分支 nodeId；主干返回 null */
    public static Long lastNodeId(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        int i = path.lastIndexOf("/n/");
        String tail = i < 0 ? path : path.substring(i + 3);
        if (tail.startsWith("n/")) {
            tail = tail.substring(2);
        }
        try {
            return Long.valueOf(tail);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 路径深度 = 'n/' 段数，如 '' -> 0，'n/12' -> 1，'n/12/n/34' -> 2 */
    public static int pathDepth(String path) {
        if (path == null || path.isEmpty()) {
            return 0;
        }
        return path.split("n/", -1).length - 1;
    }
}
