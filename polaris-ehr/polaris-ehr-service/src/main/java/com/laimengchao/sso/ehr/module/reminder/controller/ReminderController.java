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
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) String type) {
        return R.ok(reminderService.my(unreadOnly, type, page, size));
    }

    @GetMapping("/unread-count")
    public R<Integer> unreadCount(@RequestParam(required = false) String type) {
        return R.ok(reminderService.unreadCount(type));
    }

    @PostMapping("/{id}/read")
    public R<Void> read(@PathVariable Long id) {
        reminderService.markRead(id);
        return R.ok(null);
    }

    /** 手动触发一次试用期提醒生成（补跑历史超期数据用，幂等） */
    @PostMapping("/generate-probation")
    public R<Integer> generateProbation() {
        return R.ok(reminderService.generateDaily());
    }

    @PostMapping("/read-all")
    public R<Void> readAll() {
        reminderService.markAllRead();
        return R.ok(null);
    }
}
