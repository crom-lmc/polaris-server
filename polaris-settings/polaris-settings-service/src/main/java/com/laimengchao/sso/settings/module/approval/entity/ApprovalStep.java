package com.laimengchao.sso.settings.module.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批步骤：串行单元。
 * <p>
 * v5：steps 仍是扁平数组，但 seq 改为 <b>DFS 前序全局编号</b>。
 * parentNodeId / branchPath / depth 描述「这一步属于哪条条件分支」：
 * 主干步骤三者为空 / '' / 0；条件网关下某分支的专属步骤，
 * parentNodeId 指向该分支的 node，branchPath = 父路径 + "/n/" + nodeId，
 * depth = 父 depth + 1（上限 3）。引擎按 path 寻址，不再线性扫全局 steps。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cfg_approval_step")
public class ApprovalStep extends BaseEntity {

    private Long templateId;
    /** 步骤序号，从 1 连续，按 DFS 前序编排（主干 → 网关 → 各分支子链 → 主干后续） */
    private Integer seq;
    private String name;
    /** 步骤级条件，空=总执行；为假整步跳过 */
    private String conditionExpr;
    private String conditionDesc;
    /** v5 起统一为整单 END；字段保留是为了能读出历史模板，引擎不再按 PREV/NODE 分叉 */
    private String rejectAction;

    /** 所属条件分支节点 ID；NULL=主干或普通步骤 */
    private Long parentNodeId;
    /** 路径键：主干 ''；分支子链 'n/&lt;nodeId&gt;'，多段用 / 连接 */
    private String branchPath;
    /** 嵌套深度：主干 0，第 N 层分支子链 N，上限 3 */
    private Integer depth;
    /** 编辑器提示：APPROVER / GATEWAY / PARALLEL / END，引擎不读 */
    private String uiKind;
    /** 时效小时数：该步骤停留超过此值触发超时提醒，空=不提醒 */
    private Integer slaHours;
}
