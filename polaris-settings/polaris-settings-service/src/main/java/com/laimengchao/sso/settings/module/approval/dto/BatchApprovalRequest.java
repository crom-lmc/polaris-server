package com.laimengchao.sso.settings.module.approval.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量审批请求：一次对多条待办做同一种动作。
 * <p>
 * 逐条校验（属于当前用户、实例进行中），单条失败不影响其它条，
 * 结果见 {@code BatchApprovalResultVO}。
 */
@Data
public class BatchApprovalRequest {

    /** 待办任务 ID；前端按字符串传，Jackson 自动转 Long */
    private List<Long> taskIds = new ArrayList<>();
    /** 统一动作：APPROVE 通过 / REJECT 驳回（大小写不敏感） */
    private String action;
    /** 统一审批意见 */
    private String comment;
    /** 当前操作人（内部接口由账号中心传入） */
    private Long operatorId;
    private String operatorName;
}
