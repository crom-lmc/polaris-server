package com.laimengchao.sso.ehr.module.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工作交接项目：一张离职/减员单挂多条，由交接人逐条确认。
 * <p>
 * 全部 {@code status=1} 之后申请单的 {@code handoverStatus} 才会变成 1，
 * 负责人才被允许审批 —— 「交接没做完不能走」这件事就落在这一张表上。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_handover_item")
public class HandoverItem extends BaseEntity {

    /** 所属离职/减员申请ID */
    private Long applyId;
    /** 所属员工ID（被交接的离职/减员员工）：冗余一份，便于跨单按人看交接历史、按人反查 */
    private Long employeeId;
    /** 排序号，按提交时的顺序 1..n */
    private Integer seq;
    private String content;
    /** 0 待确认 / 1 已确认 */
    private Integer status;
    /** 确认人账户ID（= 交接人） */
    private Long confirmerAccountId;
    private LocalDateTime confirmedAt;
    /** 确认备注 / 遗留问题 */
    private String note;
}
