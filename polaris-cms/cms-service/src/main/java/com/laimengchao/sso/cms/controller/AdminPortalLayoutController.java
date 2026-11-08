package com.laimengchao.sso.cms.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.cms.module.portal.entity.PortalLayout;
import com.laimengchao.sso.cms.module.portal.service.PortalLayoutService;
import com.laimengchao.sso.cms.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 门户首页布局管理（CMS 后台维护，门户只读公开接口） */
@RestController
@RequestMapping("/api/cms/portal-layout")
@RequiredArgsConstructor
public class AdminPortalLayoutController {

    private final PortalLayoutService portalLayoutService;

    /** 全部模块（含隐藏），供后台配置页展示 */
    @GetMapping
    @Permit("portal:view")
    public R<List<PortalLayout>> list() {
        return R.ok(portalLayoutService.listAll());
    }

    /** 保存布局：传入按顺序排好的模块列表 */
    @PutMapping
    @Permit("portal:config")
    public R<List<PortalLayout>> save(@RequestBody List<PortalLayout> items) {
        return R.ok(portalLayoutService.saveLayout(items));
    }
}
