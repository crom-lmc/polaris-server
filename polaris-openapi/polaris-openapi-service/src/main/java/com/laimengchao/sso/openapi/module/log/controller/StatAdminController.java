package com.laimengchao.sso.openapi.module.log.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.openapi.module.log.service.StatService;
import com.laimengchao.sso.openapi.module.log.vo.LogQuery;
import com.laimengchao.sso.openapi.security.admin.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 统计报表管理接口（SSO 用户令牌 + @Permit）。概览页与统计报表页共用。 */
@RestController
@RequestMapping("/api/open/admin/stat")
@RequiredArgsConstructor
public class StatAdminController {

    private final StatService statService;

    @GetMapping("/overview")
    @Permit(PermissionCodes.OPENAPI_STAT_VIEW)
    public R<Map<String, Object>> overview(@RequestParam(required = false) String direction,
                                           @RequestParam(required = false) String appCode,
                                           @RequestParam(required = false) String apiCode,
                                           @RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to) {
        LogQuery q = new LogQuery();
        q.setDirection(direction);
        q.setAppCode(appCode);
        q.setApiCode(apiCode);
        q.setFrom(from);
        q.setTo(to);
        return R.ok(statService.overview(q));
    }
}
