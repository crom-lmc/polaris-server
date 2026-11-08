package com.laimengchao.sso.cms.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.cms.module.banner.entity.Banner;
import com.laimengchao.sso.cms.module.banner.mapper.BannerMapper;
import com.laimengchao.sso.cms.module.content.entity.Article;
import com.laimengchao.sso.cms.module.content.mapper.ArticleMapper;
import com.laimengchao.sso.cms.module.media.entity.Media;
import com.laimengchao.sso.cms.module.media.mapper.MediaMapper;
import com.laimengchao.sso.cms.security.Permit;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仪表盘统计 */
@RestController
@RequestMapping("/api/cms/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ArticleMapper articleMapper;
    private final MediaMapper mediaMapper;
    private final BannerMapper bannerMapper;

    @GetMapping
    @Permit("dashboard:view")
    public R<Stats> stats() {
        Stats s = new Stats();
        s.setTotal(articleMapper.selectCount(new LambdaQueryWrapper<Article>()));
        s.setPublished(articleMapper.selectCount(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, Article.STATUS_PUBLISHED)));
        // 「待审核」= 审批中心在途(6) + 历史本地待审核(1)，两者都还没发布
        s.setPendingAudit(articleMapper.selectCount(new LambdaQueryWrapper<Article>()
                .in(Article::getStatus, Article.STATUS_APPROVING, Article.STATUS_PENDING)));
        s.setDraft(articleMapper.selectCount(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, Article.STATUS_DRAFT)));
        s.setRecycled(articleMapper.selectCount(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, -1)));
        s.setMedia(mediaMapper.selectCount(new LambdaQueryWrapper<Media>()));
        s.setBanners(bannerMapper.selectCount(new LambdaQueryWrapper<Banner>()));
        return R.ok(s);
    }

    @Data
    public static class Stats {
        private Long total;
        private Long published;
        private Long pendingAudit;
        private Long draft;
        private Long recycled;
        private Long media;
        private Long banners;
    }
}
