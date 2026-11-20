package com.laimengchao.sso.forum.module.overview.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.forum.security.Permit;
import com.laimengchao.sso.forum.module.overview.service.OverviewService;
import com.laimengchao.sso.forum.module.overview.vo.OverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 论坛管理端「北极星核」概览聚合统计。
 * <p>
 * 与账号中心概览一致：社区规模信息，不含任何个人隐私或密钥，
 * 作为管理端首页让所有运营角色都能看到社区全貌。
 * <p>
 * 但仍是管理端接口，挂 {@code forum:overview:view} 权限码：
 * 三个运营角色 + 论坛管理员均授予该码，其它账号无法绕过页面直接取数。
 */
@RestController
@RequestMapping("/api/forum/admin")
@RequiredArgsConstructor
public class OverviewController {

    private final OverviewService overviewService;

    @GetMapping("/overview")
    @Permit(PermissionCodes.FORUM_OVERVIEW_VIEW)
    public R<OverviewVO> overview() {
        return R.ok(overviewService.overview());
    }
}
