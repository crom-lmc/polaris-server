package com.laimengchao.sso.forum.module.draft.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.forum.module.draft.dto.DraftRequest;
import com.laimengchao.sso.forum.module.draft.service.DraftService;
import com.laimengchao.sso.forum.module.draft.vo.DraftVO;
import com.laimengchao.sso.forum.module.forum.vo.ThreadDetailVO;
import com.laimengchao.sso.forum.security.Permit;
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

@RestController
@RequestMapping("/api/forum/drafts")
@RequiredArgsConstructor
public class DraftController {

    private final DraftService draftService;

    @GetMapping
    public R<List<DraftVO>> list() {
        return R.ok(draftService.list());
    }

    @PostMapping
    @Permit(value = PermissionCodes.FORUM_DRAFT, app = PermissionCodes.APP_FORUM)
    public R<DraftVO> create(@RequestBody DraftRequest body) {
        return R.ok(draftService.create(body));
    }

    @GetMapping("/{id}")
    public R<DraftVO> get(@PathVariable long id) {
        return R.ok(draftService.get(id));
    }

    @PutMapping("/{id}")
    @Permit(value = PermissionCodes.FORUM_DRAFT, app = PermissionCodes.APP_FORUM)
    public R<DraftVO> update(@PathVariable long id, @RequestBody DraftRequest body) {
        return R.ok(draftService.update(id, body));
    }

    @DeleteMapping("/{id}")
    @Permit(value = PermissionCodes.FORUM_DRAFT, app = PermissionCodes.APP_FORUM)
    public R<Void> delete(@PathVariable long id) {
        draftService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/publish")
    @Permit(value = PermissionCodes.FORUM_DRAFT, app = PermissionCodes.APP_FORUM)
    public R<ThreadDetailVO> publish(@PathVariable long id) {
        return R.ok(draftService.publish(id));
    }
}
