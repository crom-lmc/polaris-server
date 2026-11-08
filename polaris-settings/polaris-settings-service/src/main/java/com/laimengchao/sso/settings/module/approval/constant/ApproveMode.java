package com.laimengchao.sso.settings.module.approval.constant;

/**
 * 分支内审批人聚合方式。
 * <ul>
 *   <li>ANY 或签（竞争）：任一人通过即分支通过，其余任务立即作废——「谁先处理算谁的」</li>
 *   <li>ALL 会签（并行）：分支内全部通过才通过，任一人驳回即整分支驳回</li>
 *   <li>SEQ 依次：按 seq_in_node 顺序逐个派单，前一个通过才激活下一个</li>
 * </ul>
 */
public enum ApproveMode {

    ANY("或签"),
    ALL("会签"),
    SEQ("依次");

    private final String label;

    ApproveMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ApproveMode of(String v) {
        if (v == null || v.isBlank()) {
            return ANY;
        }
        try {
            return valueOf(v.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ANY;
        }
    }
}
