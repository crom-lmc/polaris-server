package com.laimengchao.sso.account.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 审批动作请求（审批中心前端提交）。
 * <p>
 * 刻意<b>不含 operatorId</b>：操作人一律由 Controller 从当前令牌里取，
 * 让前端传「我是谁」等于把越权入口敞开。审批中心只信任网关注入的登录态。
 */
@Data
public class ApprovalActionRequest {

    /** true 通过 / false 驳回；转办、加签等动作忽略 */
    private Boolean pass;

    /** 审批意见 */
    private String note;

    /** 转办 / 加签的目标账号；雪花 ID 用字符串回传避免前端丢精度 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;

    /** 加签位置：BEFORE 前加签 / AFTER 后加签 */
    private String position;
}
