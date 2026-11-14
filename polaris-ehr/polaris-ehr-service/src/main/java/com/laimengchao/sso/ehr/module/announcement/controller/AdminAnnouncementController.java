package com.laimengchao.sso.ehr.module.announcement.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.ehr.module.announcement.dto.AnnouncementSaveRequest;
import com.laimengchao.sso.ehr.module.announcement.service.AnnouncementService;
import com.laimengchao.sso.ehr.module.announcement.vo.AnnouncementVO;
import com.laimengchao.sso.ehr.module.org.service.OrgService;
import com.laimengchao.sso.ehr.module.org.vo.OrgNodeVO;
import com.laimengchao.sso.ehr.security.Permit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 通知管理（人力中心）：维护内部通知并发布到统一账号中心「通知中心」。
 * 统一用 {@code announcement:manage} 一个码，避免「能建不能发」这类半吊子授权。
 */
@RestController
@RequestMapping("/api/ehr/announcements/manage")
@RequiredArgsConstructor
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;
    private final OrgService orgService;

    /**
     * 通知目标选择的组织树：单独开一个挂在 announcement:manage 下的只读入口。
     * 直接复用 /api/ehr/orgs/tree 会额外要求 org:view——只有通知维护权的运营角色会被 403。
     */
    @GetMapping("/org-tree")
    @Permit(PermissionCodes.ANNOUNCEMENT_MANAGE)
    public R<List<OrgNodeVO>> orgTree() {
        return R.ok(orgService.tree(null));
    }

    @GetMapping
    @Permit(PermissionCodes.ANNOUNCEMENT_MANAGE)
    public R<R.Page<AnnouncementVO>> page(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(announcementService.page(status, category, keyword, page, size));
    }

    @GetMapping("/{id}")
    @Permit(PermissionCodes.ANNOUNCEMENT_MANAGE)
    public R<AnnouncementVO> detail(@PathVariable Long id) {
        return R.ok(announcementService.detail(id));
    }

    @PostMapping
    @Permit(PermissionCodes.ANNOUNCEMENT_MANAGE)
    public R<AnnouncementVO> create(@Valid @RequestBody AnnouncementSaveRequest req) {
        return R.ok(announcementService.create(req));
    }

    @PutMapping("/{id}")
    @Permit(PermissionCodes.ANNOUNCEMENT_MANAGE)
    public R<AnnouncementVO> update(@PathVariable Long id,
                                    @Valid @RequestBody AnnouncementSaveRequest req) {
        return R.ok(announcementService.update(id, req));
    }

    /** 草稿 / 已下架 → 已发布（同时快照应读人数） */
    @PostMapping("/{id}/publish")
    @Permit(PermissionCodes.ANNOUNCEMENT_MANAGE)
    public R<AnnouncementVO> publish(@PathVariable Long id) {
        return R.ok(announcementService.publish(id));
    }

    @PostMapping("/{id}/offline")
    @Permit(PermissionCodes.ANNOUNCEMENT_MANAGE)
    public R<AnnouncementVO> offline(@PathVariable Long id) {
        return R.ok(announcementService.offline(id));
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.ANNOUNCEMENT_MANAGE)
    public R<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return R.ok();
    }
}
