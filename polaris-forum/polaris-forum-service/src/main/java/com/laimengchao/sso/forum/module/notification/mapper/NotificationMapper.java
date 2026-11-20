package com.laimengchao.sso.forum.module.notification.mapper;

import com.laimengchao.sso.forum.module.notification.dto.NotificationListDTO;
import com.laimengchao.sso.forum.module.notification.dto.NotificationUnreadDTO;
import com.laimengchao.sso.forum.module.notification.entity.Notification;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NotificationMapper {

    int insert(Notification values);

    List<NotificationListDTO> list(@Param("accountId") long accountId, @Param("type") String type,
                                   @Param("unread") Boolean unread);

    int unreadCount(@Param("accountId") long accountId);

    List<NotificationUnreadDTO> unreadCounts(@Param("accountId") long accountId);

    int markRead(@Param("id") long id, @Param("accountId") long accountId);

    int markAllRead(@Param("accountId") long accountId);
}
