package com.laimengchao.sso.forum.module.level.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.forum.module.level.dto.LevelRequest;
import com.laimengchao.sso.forum.module.level.service.LevelService;
import com.laimengchao.sso.forum.module.level.vo.LevelVO;
import com.laimengchao.sso.forum.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/forum/admin/levels")
@RequiredArgsConstructor
public class AdminLevelController {

    private final LevelService levelService;

    @Permit(PermissionCodes.FORUM_LEVEL_VIEW)
    @GetMapping
    public R<List<LevelVO>> list() {
        return R.ok(levelService.adminList());
    }

    @Permit(PermissionCodes.FORUM_LEVEL_CREATE)
    @PostMapping
    public R<LevelVO> create(@RequestBody LevelRequest body) {
        return R.ok(levelService.create(body));
    }

    @Permit(PermissionCodes.FORUM_LEVEL_EDIT)
    @PatchMapping("/{id}")
    public R<LevelVO> update(@PathVariable long id, @RequestBody LevelRequest body) {
        return R.ok(levelService.update(id, body));
    }

    @Permit(PermissionCodes.FORUM_LEVEL_DELETE)
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable long id) {
        levelService.delete(id);
        return R.ok();
    }
}
