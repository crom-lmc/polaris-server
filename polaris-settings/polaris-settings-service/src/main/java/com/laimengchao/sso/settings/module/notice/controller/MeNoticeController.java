package com.laimengchao.sso.settings.module.notice.controller;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.settings.module.notice.service.NoticeService;
import com.laimengchao.sso.settings.module.notice.vo.NoticeMessageVO;
import com.laimengchao.sso.settings.security.SecurityUser;
import com.laimengchao.sso.settings.security.SecurityUtils;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 我的待办/消息：统一账号中心右上角消息铃铛直接调用（登录即可，无按钮权限）。
 * 令牌由网关透传，账号ID 一律取自当前登录身份，前端传什么账号都不作数。
 */
@RestController
@RequestMapping("/api/settings/notices")
public class MeNoticeController {

    private final NoticeService noticeService;

    public MeNoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping("/my")
    public R<List<NoticeMessageVO>> my(@RequestParam(required = false) Integer kind,
                                       @RequestParam(required = false) Boolean unreadOnly,
                                       @RequestParam(required = false) Integer limit) {
        return R.ok(noticeService.myMessages(currentAccountId(), kind, unreadOnly, limit));
    }

    /** kind 不传=全部未读；1=待办未读 2=消息未读（控制台三个数字卡片按 kind 分别取） */
    @GetMapping("/my/unread-count")
    public R<Long> unreadCount(@RequestParam(required = false) Integer kind) {
        return R.ok(noticeService.unreadCount(currentAccountId(), kind));
    }

    @PostMapping("/my/read")
    public R<Integer> markRead(@RequestBody ReadRequest body) {
        return R.ok(noticeService.markRead(currentAccountId(), body.getIds()));
    }

    @PostMapping("/my/read-all")
    public R<Integer> markAllRead() {
        return R.ok(noticeService.markAllRead(currentAccountId()));
    }

    private Long currentAccountId() {
        SecurityUser user = SecurityUtils.currentUser()
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
        return user.getAccountId();
    }

    @Data
    public static class ReadRequest {
        private List<Long> ids;
    }
}
