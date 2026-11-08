package com.laimengchao.sso.ehr.module.announcement.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.announcement.service.AnnouncementService;
import com.laimengchao.sso.ehr.module.announcement.vo.AnnouncementVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知中心（统一账号中心调用）：只返回「已发布 且 对当前登录人可见」的通知。
 * <p>
 * 不加 @Permit：账号中心令牌作用域是 sso-center，会被 PermissionAspect 跨应用拒绝；
 * 这里改为在 Service 内校验登录，并按员工档案的组织祖先链过滤可见范围。
 */
@RestController
@RequestMapping("/api/ehr/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping
    public R<R.Page<AnnouncementVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return R.ok(announcementService.myPage(page, size, category, keyword, unreadOnly));
    }

    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        return R.ok(announcementService.myUnreadCount());
    }

    /** 详情：打开即记读，返回富文本正文（HTML） */
    @GetMapping("/{id}")
    public R<AnnouncementVO> detail(@PathVariable Long id) {
        return R.ok(announcementService.myDetail(id));
    }
}
