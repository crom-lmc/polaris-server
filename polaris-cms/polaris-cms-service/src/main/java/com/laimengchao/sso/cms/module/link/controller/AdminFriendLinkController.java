package com.laimengchao.sso.cms.module.link.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.cms.module.link.entity.FriendLink;
import com.laimengchao.sso.cms.module.link.service.FriendLinkService;
import com.laimengchao.sso.cms.security.Permit;
import com.laimengchao.sso.cms.constant.PermissionCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 友情链接管理（门户首页「友情链接」模块的数据源） */
@RestController
@RequestMapping("/api/cms/friend-links")
@RequiredArgsConstructor
public class AdminFriendLinkController {

    private final FriendLinkService friendLinkService;

    @GetMapping
    @Permit(PermissionCodes.LINK_VIEW)
    public R<List<FriendLink>> list() {
        return R.ok(friendLinkService.listAll());
    }

    @PostMapping
    @Permit(PermissionCodes.LINK_MANAGE)
    public R<FriendLink> create(@RequestBody FriendLink link) {
        link.setId(null);
        return R.ok(friendLinkService.save(link));
    }

    @PutMapping("/{id}")
    @Permit(PermissionCodes.LINK_MANAGE)
    public R<FriendLink> update(@PathVariable Long id, @RequestBody FriendLink link) {
        link.setId(id);
        return R.ok(friendLinkService.save(link));
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.LINK_MANAGE)
    public R<Void> delete(@PathVariable Long id) {
        friendLinkService.delete(id);
        return R.ok();
    }
}
