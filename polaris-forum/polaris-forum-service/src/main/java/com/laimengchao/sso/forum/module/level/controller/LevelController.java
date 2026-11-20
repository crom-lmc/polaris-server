package com.laimengchao.sso.forum.module.level.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.forum.module.level.service.LevelService;
import com.laimengchao.sso.forum.module.level.vo.LevelRouteVO;
import com.laimengchao.sso.forum.module.level.vo.LevelVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/forum/public/levels")
@RequiredArgsConstructor
public class LevelController {

    private final LevelService levelService;

    /** 社区等级晋升路线（公开可读，登录用户附带当前进度） */
    @GetMapping
    public R<LevelRouteVO> route() {
        return R.ok(levelService.currentRoute());
    }

    /** 仅等级列表，供各处徽章展示 */
    @GetMapping("/definitions")
    public R<List<LevelVO>> definitions() {
        return R.ok(levelService.definitions());
    }
}
