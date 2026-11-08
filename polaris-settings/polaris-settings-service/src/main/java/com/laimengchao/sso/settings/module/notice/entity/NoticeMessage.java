package com.laimengchao.sso.settings.module.notice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 站内待办/消息：统一账号中心右上角「消息」里展示。
 * kind=1 待办（有处理动作），kind=2 消息（纯通知）；read_at 为空即未读。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notice_message")
public class NoticeMessage extends BaseEntity {

    /** 接收人账号ID */
    private Long accountId;
    /** 触发规则编码 */
    private String ruleCode;
    /** 1 待办 / 2 消息 */
    private Integer kind;
    private String module;
    private String title;
    private String content;
    private String bizType;
    private String bizId;
    /** 业务编码：调用方自定义的业务标识，如审批单号 */
    private String bizNo;
    /** 发起人：待办为申请人，审批结果消息为实际通过/驳回的人 */
    private String applicantName;
    /** 点击跳转的前端路由 */
    private String link;
    /** 已读时间，null 表示未读 */
    private LocalDateTime readAt;
}
