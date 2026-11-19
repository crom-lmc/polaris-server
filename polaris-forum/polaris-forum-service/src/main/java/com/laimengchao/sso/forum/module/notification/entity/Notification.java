package com.laimengchao.sso.forum.module.notification.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class Notification {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recipientAccountId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long actorAccountId;
    private String type;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bizId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long threadId;
    private String title;
    private String summary;
    private String readAt;
    private String createdAt;
}
