package com.laimengchao.sso.forum.module.forum.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.forum.module.attachment.service.AttachmentService;
import com.laimengchao.sso.forum.module.banner.service.BannerService;
import com.laimengchao.sso.forum.module.banner.vo.BannerVO;
import com.laimengchao.sso.forum.module.badge.service.BadgeService;
import com.laimengchao.sso.forum.module.badge.vo.BadgeVO;
import com.laimengchao.sso.forum.module.forum.service.ForumService;
import com.laimengchao.sso.forum.module.forum.vo.CommentVO;
import com.laimengchao.sso.forum.module.forum.vo.ThreadDetailVO;
import com.laimengchao.sso.forum.module.forum.vo.ThreadPageVO;
import com.laimengchao.sso.forum.module.forum.vo.ThreadVO;
import com.laimengchao.sso.forum.module.interaction.service.FollowService;
import com.laimengchao.sso.forum.module.interaction.vo.FollowRelationVO;
import com.laimengchao.sso.forum.module.member.service.MemberService;
import com.laimengchao.sso.forum.module.member.vo.PointRankingVO;
import com.laimengchao.sso.forum.module.member.vo.PublicMemberVO;
import com.laimengchao.sso.forum.module.reward.service.RewardService;
import com.laimengchao.sso.forum.module.reward.vo.FeatureFlagsVO;
import com.laimengchao.sso.forum.module.reward.vo.PublicRewardsVO;
import com.laimengchao.sso.forum.module.site.service.SiteService;
import com.laimengchao.sso.forum.module.site.vo.PublicSiteConfigVO;
import com.laimengchao.sso.forum.module.taxonomy.service.TaxonomyService;
import com.laimengchao.sso.forum.module.taxonomy.vo.BoardVO;
import com.laimengchao.sso.forum.module.taxonomy.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/forum/public")
@RequiredArgsConstructor
public class PublicForumController {

    private final ForumService forumService;
    private final RewardService rewardService;
    private final AttachmentService attachmentService;
    private final MemberService memberService;
    private final BadgeService badgeService;
    private final FollowService followService;
    private final TaxonomyService taxonomyService;
    private final SiteService siteService;
    private final BannerService bannerService;

    @GetMapping("/site")
    public R<PublicSiteConfigVO> site() {
        return R.ok(siteService.publicConfig());
    }

    @GetMapping("/banners")
    public R<List<BannerVO>> banners() {
        return R.ok(bannerService.publicBanners());
    }

    @GetMapping("/boards")
    public R<List<BoardVO>> boards() {
        return R.ok(forumService.boards());
    }

    @GetMapping("/tags")
    public R<List<TagVO>> tags(@RequestParam String board) {
        return R.ok(taxonomyService.tags(board));
    }

    @GetMapping("/threads")
    public R<List<ThreadVO>> threads(@RequestParam(required = false) String kind,
                                     @RequestParam(required = false) String board,
                                     @RequestParam(required = false) String q,
                                     @RequestParam(defaultValue = "latest") String sort) {
        return R.ok(forumService.list(kind, board, q, sort));
    }

    /** 服务端分页列表：只返回当前页摘要（不含正文），供首页/内容列表快速渲染。 */
    @GetMapping("/threads/page")
    public R<ThreadPageVO> threadPage(@RequestParam(required = false) String kind,
                                      @RequestParam(required = false) String board,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(defaultValue = "latest") String sort,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return R.ok(forumService.listPage(kind, board, q, sort, page, size));
    }

    @GetMapping("/threads/{id}")
    public R<ThreadDetailVO> thread(@PathVariable long id) {
        return R.ok(forumService.detail(id));
    }

    @GetMapping("/threads/{id}/comments")
    public R<List<CommentVO>> comments(@PathVariable long id) {
        return R.ok(forumService.comments(id));
    }

    @GetMapping("/attachments/{id}")
    public ResponseEntity<Resource> attachment(@PathVariable long id) {
        return attachmentService.download(id, true);
    }

    @GetMapping("/rewards")
    public R<PublicRewardsVO> rewards() {
        return R.ok(rewardService.publicRewards());
    }

    /** 功能开关（抽奖/兑换）来自配置中心埋点，供前台隐藏入口、后台置灰按钮 */
    @GetMapping("/features")
    public R<FeatureFlagsVO> features() {
        return R.ok(rewardService.featureFlags());
    }

    @GetMapping("/members/{accountId}")
    public R<PublicMemberVO> member(@PathVariable long accountId) {
        return R.ok(memberService.publicMember(accountId));
    }

    @GetMapping("/members/{accountId}/relations")
    public R<List<FollowRelationVO>> relations(@PathVariable long accountId, @RequestParam String type) {
        return R.ok(followService.relations(accountId, type));
    }

    @GetMapping("/members/{accountId}/threads")
    public R<List<ThreadVO>> memberThreads(@PathVariable long accountId,
                                            @RequestParam(required = false) String kind) {
        return R.ok(memberService.memberThreads(accountId, kind));
    }

    @GetMapping("/ranking/points")
    public R<List<PointRankingVO>> pointRanking(@RequestParam(defaultValue = "10") Integer limit) {
        return R.ok(memberService.pointRanking(limit));
    }

    @GetMapping("/ranking/level")
    public R<List<PointRankingVO>> levelRanking(@RequestParam(defaultValue = "10") Integer limit) {
        return R.ok(memberService.levelRanking(limit));
    }

    @GetMapping("/badges")
    public R<List<BadgeVO>> badges() {
        return R.ok(badgeService.publicBadges());
    }
}
