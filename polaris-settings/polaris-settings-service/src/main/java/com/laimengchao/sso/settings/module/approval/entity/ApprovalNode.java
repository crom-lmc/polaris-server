package com.laimengchao.sso.settings.module.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批分支：步骤内的并行单元。
 * <p>
 * 一个步骤下多个分支并行推进，全部生效分支都通过才进入下一步骤。
 * approvers 是 JSON 数组（如 {@code [{"type":"ORG_LEADER","levelOffset":0}]}），
 * 整体替换式保存，不拆子表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cfg_approval_node")
public class ApprovalNode extends BaseEntity {

    private Long templateId;
    private Long stepId;
    /** 步骤内顺序 */
    private Integer seq;
    private String name;
    /** 分支条件，空=总生效 */
    private String conditionExpr;
    private String conditionDesc;
    /** 审批人规则 JSON 数组 */
    private String approvers;
    /** ANY 或签 / ALL 会签 / SEQ 依次 */
    private String approveMode;
    /** 允许审批自己提交的单据 */
    private Integer allowSelf;
    /** 与上一步骤实际审批人相同时跳过 */
    private Integer skipSame;
    /** 解析不到审批人：SKIP 跳过 / BLOCK 阻塞 */
    private String onEmpty;
    private Integer timeoutHours;

    /** 兜底分支：同一网关其它分支全部未命中时才生效；同一网关最多 1 条 */
    private Integer isFallback;
    /** 该分支是否挂了独立子链（编辑器冗余标记，以 cfg_approval_step.parent_node_id 为准） */
    private Integer childChain;
}
