package com.laimengchao.sso.forum.module.notification.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class NotificationVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recipientAccountId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long actorAccountId;
    private String actorName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long actorAvatarAttachmentId;
    private String type;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bizId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long threadId;
    private String threadTitle;
    private String title;
    private String summary;
    private String readAt;
    private String createdAt;
    private Boolean read;
    private String actorAvatarUrl;
}
