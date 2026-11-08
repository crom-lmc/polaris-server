package com.laimengchao.sso.cms.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.cms.module.banner.entity.Banner;
import com.laimengchao.sso.cms.module.banner.mapper.BannerMapper;
import com.laimengchao.sso.cms.module.content.dto.ArticleVO;
import com.laimengchao.sso.cms.module.content.dto.TagVO;
import com.laimengchao.sso.cms.module.content.entity.Article;
import com.laimengchao.sso.cms.module.content.service.ArticleService;
import com.laimengchao.sso.cms.module.content.service.CategoryService;
import com.laimengchao.sso.cms.module.content.service.TagService;
import com.laimengchao.sso.cms.module.link.entity.FriendLink;
import com.laimengchao.sso.cms.module.link.service.FriendLinkService;
import com.laimengchao.sso.cms.module.portal.entity.PortalLayout;
import com.laimengchao.sso.cms.module.portal.service.PortalLayoutService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台读者接口（公开，无需登录；网关白名单 /api/cms/public/**）。
 * 只暴露已发布内容与启用的 Banner，供官网/小程序/博客主题取数。
 */
@RestController
@RequestMapping("/api/cms/public")
@RequiredArgsConstructor
public class PublicContentController {

    private final ArticleService articleService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final BannerMapper bannerMapper;
    private final PortalLayoutService portalLayoutService;
    private final FriendLinkService friendLinkService;

    @GetMapping("/articles")
    public R<R.Page<ArticleVO>> articles(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Long categoryId,
                                         @RequestParam(required = false) Long tagId,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        return R.ok(articleService.publicPage(keyword, categoryId, tagId, page, size));
    }

    @GetMapping("/search")
    public R<List<ArticleVO>> search(@RequestParam String keyword,
                                     @RequestParam(defaultValue = "20") int limit) {
        return R.ok(articleService.search(keyword, limit));
    }

    @GetMapping("/articles/{id}")
    public R<ArticleVO> detail(@PathVariable Long id) {
        return R.ok(articleService.publicDetail(id));
    }

    @GetMapping("/categories")
    public R<List<TagVO>> categories() {
        return R.ok(categoryService.listAll());
    }

    @GetMapping("/tags")
    public R<List<TagVO>> tags() {
        return R.ok(tagService.listAll(null));
    }

    @GetMapping("/banners")
    public R<List<Banner>> banners() {
        LambdaQueryWrapper<Banner> w = new LambdaQueryWrapper<>();
        w.eq(Banner::getStatus, 1);
        w.orderByAsc(Banner::getSort);
        w.orderByAsc(Banner::getId);
        return R.ok(bannerMapper.selectList(w));
    }

    /** 门户首页布局：已启用模块，按 sort 排（门户据此动态渲染区块） */
    @GetMapping("/layout")
    public R<List<PortalLayout>> layout() {
        return R.ok(portalLayoutService.listEnabled());
    }

    /** 门户「友情链接」模块：仅启用的站点，按 sort 排 */
    @GetMapping("/links")
    public R<List<FriendLink>> links() {
        return R.ok(friendLinkService.listEnabled());
    }
}
