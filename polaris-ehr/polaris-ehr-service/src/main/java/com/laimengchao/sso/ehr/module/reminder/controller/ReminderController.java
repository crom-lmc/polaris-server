package com.laimengchao.sso.ehr.module.reminder.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.reminder.entity.Reminder;
import com.laimengchao.sso.ehr.module.reminder.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 我的到期提醒。只返回当前登录账号自己的提醒，不需要额外权限码；
 * 未读徽标与看板卡片都用这里的接口。
 */
@RestController
@RequestMapping("/api/ehr/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    @GetMapping("/my")
    public R<R.Page<Reminder>> my(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return R.ok(reminderService.my(unreadOnly, page, size));
    }

    @GetMapping("/unread-count")
    public R<Integer> unreadCount() {
        return R.ok(reminderService.unreadCount());
    }

    @PostMapping("/{id}/read")
    public R<Void> read(@PathVariable Long id) {
        reminderService.markRead(id);
        return R.ok(null);
    }

    @PostMapping("/read-all")
    public R<Void> readAll() {
        reminderService.markAllRead();
        return R.ok(null);
    }
}
