package com.laimengchao.sso.forum.module.notification.dto;

import lombok.Data;

/** 按类型分组的未读消息数（通知列表页各分类角标用）。 */
@Data
public class NotificationUnreadDTO {

    private String type;

    private Integer count;
}
