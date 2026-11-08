package com.laimengchao.sso.settings.client.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨服务发送待办/消息的入参。
 * <p>
 * 只给「事件编码 + 接收人 + 变量」，标题与内容由设置中心配置页维护的模板渲染；
 * 未配置规则时可自带 title/content 兜底。
 */
@Data
public class NoticeSendDTO {

    /** 事件编码，如 approval.approved */
    private String ruleCode;
    /** 接收人账号ID */
    private Long accountId;
    /** 群发接收人：走 /send-batch 时用，与 accountId 二选一 */
    private List<Long> accountIds;
    /** 覆盖站内方式：1 待办 2 消息；不填用规则配置 */
    private Integer channel;
    /** 模板变量 */
    private Map<String, String> vars = new HashMap<>();
    private String title;
    private String content;
    private String bizType;
    private String bizId;
    /** 业务编码：调用方自定的业务标识，如审批单号；仅展示，不参与逻辑 */
    private String bizNo;
    /** 发起人：展示在消息列表「发起人」列 */
    private String applicantName;
    /** 点击跳转的前端路由 */
    private String link;
}
