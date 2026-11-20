package com.laimengchao.sso.account.controller;

import com.laimengchao.sso.account.dto.response.OverviewVO;
import com.laimengchao.sso.account.service.OverviewService;
import com.laimengchao.sso.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号中心「概览」聚合统计。
 * <p>
 * 与 {@code /api/accounts/me} 一致：只要登录即可查看。统计口径由 Service 按当前账号的
 * 数据权限根组织收敛，有范围时只看范围内账号及其授权/登录数据，不返回全平台规模。
 */
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class OverviewController {

    private final OverviewService overviewService;

    @GetMapping("/overview")
    public R<OverviewVO> overview() {
        return R.ok(overviewService.overview());
    }
}
