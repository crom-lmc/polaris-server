package com.laimengchao.sso.forum.module.notification.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.laimengchao.sso.forum.module.notification.entity.Notification;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NotificationListDTO extends Notification {

    private String actorName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long actorAvatarAttachmentId;
    private String threadTitle;
}
