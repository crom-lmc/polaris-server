package com.laimengchao.sso.cms.module.banner.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.cms.module.banner.entity.Banner;
import com.laimengchao.sso.cms.module.banner.service.BannerService;
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

/** Banner 管理 */
@RestController
@RequestMapping("/api/cms/banners")
@RequiredArgsConstructor
public class AdminBannerController {

    private final BannerService bannerService;

    @GetMapping
    @Permit(PermissionCodes.BANNER_VIEW)
    public R<List<Banner>> list() {
        return R.ok(bannerService.listAll());
    }

    @PostMapping
    @Permit(PermissionCodes.BANNER_MANAGE)
    public R<Banner> create(@RequestBody Banner banner) {
        banner.setId(null);
        return R.ok(bannerService.save(banner));
    }

    @PutMapping("/{id}")
    @Permit(PermissionCodes.BANNER_MANAGE)
    public R<Banner> update(@PathVariable Long id, @RequestBody Banner banner) {
        banner.setId(id);
        return R.ok(bannerService.save(banner));
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.BANNER_MANAGE)
    public R<Void> delete(@PathVariable Long id) {
        bannerService.delete(id);
        return R.ok();
    }
}
