package com.laimengchao.sso.forum.module.notification.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.forum.module.notification.service.NotificationService;
import com.laimengchao.sso.forum.module.notification.vo.NotificationCountVO;
import com.laimengchao.sso.forum.module.notification.vo.NotificationReadAllVO;
import com.laimengchao.sso.forum.module.notification.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/forum/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public R<List<NotificationVO>> list(@RequestParam(required = false) String type,
                                        @RequestParam(required = false) Boolean unread) {
        return R.ok(notificationService.list(type, unread));
    }

    @GetMapping("/unread-count")
    public R<NotificationCountVO> unreadCount() {
        return R.ok(notificationService.unreadCount());
    }

    @GetMapping("/unread-counts")
    public R<Map<String, Integer>> unreadCounts() {
        return R.ok(notificationService.unreadCounts());
    }

    @PatchMapping("/{id}/read")
    public R<Void> read(@PathVariable long id) {
        notificationService.markRead(id);
        return R.ok();
    }

    @PostMapping("/read-all")
    public R<NotificationReadAllVO> readAll() {
        return R.ok(notificationService.markAllRead());
    }
}
