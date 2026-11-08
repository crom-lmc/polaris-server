package com.laimengchao.sso.settings.module.approval.vo;

import lombok.Data;

/**
 * 步骤条摘要。未开始的步骤还没有任务，靠它把步骤条画全。
 */
@Data
public class StepBriefVO {

    private Integer seq;
    private String name;
    /** DONE 已完成 / CURRENT 进行中 / TODO 未开始 / SKIPPED 条件不满足跳过 */
    private String state;
    private String conditionDesc;

    /** v5：路径键、深度、所属分支，前端据此把步骤条画成树 */
    private String branchPath;
    private Integer depth;
    private String parentNodeId;
    /** 分支展示名（命中条件 / 兜底），主干为空 */
    private String branchName;
    /** 该步骤是否为条件网关 */
    private Boolean isGateway;
    /** 等待其它分支汇合（先跑完的分支显示此态） */
    private Boolean waitingJoin;
}
