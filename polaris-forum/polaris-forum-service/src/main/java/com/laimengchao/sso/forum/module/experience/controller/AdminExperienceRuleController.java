package com.laimengchao.sso.forum.module.experience.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.forum.module.experience.dto.ExperienceRuleRequest;
import com.laimengchao.sso.forum.module.experience.service.ExperienceService;
import com.laimengchao.sso.forum.module.experience.vo.ExperienceRuleVO;
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
@RequestMapping("/api/forum/admin/experience-rules")
@RequiredArgsConstructor
public class AdminExperienceRuleController {

    private final ExperienceService experienceService;

    @Permit(PermissionCodes.FORUM_LEVEL_VIEW)
    @GetMapping
    public R<List<ExperienceRuleVO>> list() {
        return R.ok(experienceService.adminList());
    }

    @Permit(PermissionCodes.FORUM_LEVEL_CREATE)
    @PostMapping
    public R<ExperienceRuleVO> create(@RequestBody ExperienceRuleRequest body) {
        return R.ok(experienceService.create(body));
    }

    @Permit(PermissionCodes.FORUM_LEVEL_EDIT)
    @PatchMapping("/{id}")
    public R<ExperienceRuleVO> update(@PathVariable long id, @RequestBody ExperienceRuleRequest body) {
        return R.ok(experienceService.update(id, body));
    }

    @Permit(PermissionCodes.FORUM_LEVEL_DELETE)
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable long id) {
        experienceService.delete(id);
        return R.ok();
    }
}
