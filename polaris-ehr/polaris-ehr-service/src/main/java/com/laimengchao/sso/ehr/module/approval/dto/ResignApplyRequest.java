package com.laimengchao.sso.ehr.module.approval.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 离职申请 / 减员申请入参。
 * <p>
 * 「交接人 + 交接项目」是必填的：{@code handoverEmployeeId} 指定谁接手，
 * {@code items} 一项一条。确实没有在办事项时显式传 {@code noHandover=true} 跳过，
 * 而不是留空——留空等于「忘了填」，不能让单子带着空交接直接进审批。
 */
@Data
public class ResignApplyRequest {

    /** 拟离职日期 yyyy-MM-dd，为空默认取当天 */
    private String resignDate;

    @Size(max = 255, message = "原因不能超过 255 字")
    private String reason;

    /** 交接人员工ID（字符串雪花ID，前端直接传） */
    private String handoverEmployeeId;

    /** 交接项目，一项一条，最多 20 条 */
    private List<String> items;

    /** 交接备注（整体说明，可空） */
    @Size(max = 255, message = "交接说明不能超过 255 字")
    private String handover;

    /** 确实无在办事项、无需交接时置 true（会写 handoverStatus=2，跳过交接环节） */
    private Boolean noHandover;
}
