package com.laimengchao.sso.settings.module.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 业务回调补偿。
 * <p>
 * 实例进入终态后，审批中心要通知业务方「这单结束了，去改你的业务状态」。
 * 这是跨服务的最终一致性：回调失败不影响审批结果（审批确实已经通过了），
 * 但业务状态还没落。这里记录待重试，由定时任务退避重试，审批中心也能看到「业务处理失败」。
 * <p>
 * 要求业务方回调接口必须幂等（按 instanceId 去重），因为重试会重复投递。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("appr_callback")
public class ApprCallback extends BaseEntity {

    private Long instanceId;
    private String bizType;
    private String bizId;
    /** 1 通过 2 驳回 3 撤回 4 终止 */
    private Integer result;
    private String callbackUrl;
    private String payload;
    /** 0 待回调 1 成功 2 失败 */
    private Integer status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private String lastError;
}
