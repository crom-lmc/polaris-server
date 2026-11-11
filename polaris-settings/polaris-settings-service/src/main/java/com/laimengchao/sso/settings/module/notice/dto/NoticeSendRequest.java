package com.laimengchao.sso.settings.module.notice.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨服务发送待办/消息的入参。
 * <p>
 * 业务方只给「事件编码 + 接收人 + 变量」，标题与内容由运营在配置页维护的模板渲染；
 * 未配置规则时，允许调用方自带 title/content 兜底，避免因配置缺失静默丢消息。
 */
@Data
public class NoticeSendRequest {

    /** 事件编码，对应 cfg_notice_rule.code */
    private String ruleCode;
    /** 接收人账号ID */
    private Long accountId;
    /** 群发接收人：与 accountId 二选一，走 /send-batch 时用 */
    private List<Long> accountIds;
    /** 覆盖站内方式：1 待办 2 消息；不填用规则配置，规则也没有则按消息 */
    private Integer channel;
    /** 覆盖邮件开关：true 强制发邮件、false 强制不发，不填按规则配置 */
    private Boolean email;
    /** 模板变量，如 {title} / {bizName} / {opinion} */
    private Map<String, String> vars = new HashMap<>();
    /** 未配置规则时使用的标题（可选） */
    private String title;
    /** 未配置规则时使用的内容（可选） */
    private String content;
    private String bizType;
    private String bizId;
    /** 业务编码：调用方自定的业务标识，如审批单号；仅展示，不参与逻辑 */
    private String bizNo;
    /** 发起人：展示在消息列表「发起人」列，待办填申请人、审批结果填实际审批人 */
    private String applicantName;
    /** 点击跳转的前端路由（可选） */
    private String link;
}
