package com.laimengchao.sso.forum.module.badge.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.forum.module.badge.dto.BadgeRequest;
import com.laimengchao.sso.forum.module.badge.service.BadgeService;
import com.laimengchao.sso.forum.module.badge.vo.BadgeAdminVO;
import com.laimengchao.sso.forum.module.badge.vo.BadgeVO;
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

    @GetMapping
    public R<List<BadgeAdminVO>> list(@RequestParam(required = false) String status) {
        return R.ok(badgeService.adminList(status));
    }

    @PostMapping
    public R<BadgeVO> create(@RequestBody BadgeRequest body) {
        return R.ok(badgeService.create(body));
    }

    @PatchMapping("/{id}")
    public R<BadgeVO> update(@PathVariable long id, @RequestBody BadgeRequest body) {
        return R.ok(badgeService.update(id, body));
    }

    @PostMapping("/{id}/publish")
    public R<BadgeAdminVO> publish(@PathVariable long id) {
        return R.ok(badgeService.publish(id));
    }

    @PostMapping("/{id}/offline")
    public R<BadgeVO> offline(@PathVariable long id) {
        return R.ok(badgeService.offline(id));
    }
}
