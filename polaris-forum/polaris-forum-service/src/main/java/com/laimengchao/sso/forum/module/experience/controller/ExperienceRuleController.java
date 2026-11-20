package com.laimengchao.sso.forum.module.experience.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.forum.module.experience.service.ExperienceService;
import com.laimengchao.sso.forum.module.experience.vo.ExperienceRuleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/forum/public/experience-rules")
@RequiredArgsConstructor
public class ExperienceRuleController {

    private final ExperienceService experienceService;

    /** 公开的经验获取途径，供前台等级页展示 */
    @GetMapping
    public R<List<ExperienceRuleVO>> list() {
        return R.ok(experienceService.publicRules());
    }
}
