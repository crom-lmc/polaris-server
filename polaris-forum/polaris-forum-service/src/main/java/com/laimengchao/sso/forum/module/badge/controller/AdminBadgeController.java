package com.laimengchao.sso.forum.module.badge.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.forum.module.badge.dto.BadgeRequest;
import com.laimengchao.sso.forum.module.badge.service.BadgeService;
import com.laimengchao.sso.forum.module.badge.vo.BadgeAdminVO;
import com.laimengchao.sso.forum.module.badge.vo.BadgeVO;
import com.laimengchao.sso.forum.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/forum/admin/badges")
@RequiredArgsConstructor
public class AdminBadgeController {

    private final BadgeService badgeService;

    @Permit(PermissionCodes.FORUM_BADGE_VIEW)
    @GetMapping
    public R<List<BadgeAdminVO>> list(@RequestParam(required = false) String status) {
        return R.ok(badgeService.adminList(status));
    }

    @Permit(PermissionCodes.FORUM_BADGE_CREATE)
    @PostMapping
    public R<BadgeVO> create(@RequestBody BadgeRequest body) {
        return R.ok(badgeService.create(body));
    }

    @Permit(PermissionCodes.FORUM_BADGE_EDIT)
    @PatchMapping("/{id}")
    public R<BadgeVO> update(@PathVariable long id, @RequestBody BadgeRequest body) {
        return R.ok(badgeService.update(id, body));
    }

    @Permit(PermissionCodes.FORUM_BADGE_PUBLISH)
    @PostMapping("/{id}/publish")
    public R<BadgeAdminVO> publish(@PathVariable long id) {
        return R.ok(badgeService.publish(id));
    }

    @Permit(PermissionCodes.FORUM_BADGE_OFFLINE)
    @PostMapping("/{id}/offline")
    public R<BadgeVO> offline(@PathVariable long id) {
        return R.ok(badgeService.offline(id));
    }
}
