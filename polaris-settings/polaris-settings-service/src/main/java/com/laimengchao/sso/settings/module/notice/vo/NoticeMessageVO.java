package com.laimengchao.sso.settings.module.notice.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 站内待办/消息 */
@Data
public class NoticeMessageVO {
    /** 雪花ID序列化成字符串：19 位超出 JS 安全整数，置读时按 ID 回传会被抹掉后几位 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String module;
    /** 1 待办 / 2 消息 */
    private Integer kind;
    private String title;
    private String content;
    private String bizType;
    private String bizId;
    /** 业务编码，如审批单号 */
    private String bizNo;
    /** 发起人：待办为申请人，审批结果消息为实际通过/驳回的人 */
    private String applicantName;
    private String link;
    /** 左侧树分组：业务应用编码（由审批模板的 appCode 推导） */
    private String groupApp;
    /** 左侧树分组：业务类型名称，如「请假审批」 */
    private String groupName;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
