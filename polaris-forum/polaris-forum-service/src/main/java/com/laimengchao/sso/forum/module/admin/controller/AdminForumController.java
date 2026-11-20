package com.laimengchao.sso.forum.module.admin.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.forum.module.admin.dto.CommentDeleteRequest;
import com.laimengchao.sso.forum.module.admin.dto.ExchangeFulfillmentRequest;
import com.laimengchao.sso.forum.module.admin.dto.GiftRequest;
import com.laimengchao.sso.forum.module.admin.dto.SensitiveWordEnabledRequest;
import com.laimengchao.sso.forum.module.admin.dto.SensitiveWordRequest;
import com.laimengchao.sso.forum.module.admin.dto.ThreadStatusUpdateRequest;
import com.laimengchao.sso.forum.module.admin.service.AdminService;
import com.laimengchao.sso.forum.module.admin.vo.AdminCommentPageVO;
import com.laimengchao.sso.forum.module.admin.vo.AdminCommentVO;
import com.laimengchao.sso.forum.module.admin.vo.AdminThreadPageVO;
import com.laimengchao.sso.forum.module.admin.vo.AdminThreadVO;
import com.laimengchao.sso.forum.module.admin.vo.SensitiveWordPageVO;
import com.laimengchao.sso.forum.module.admin.vo.SensitiveWordVO;
import com.laimengchao.sso.forum.module.attachment.vo.AttachmentVO;
import com.laimengchao.sso.forum.module.banner.dto.BannerRequest;
import com.laimengchao.sso.forum.module.banner.service.BannerService;
import com.laimengchao.sso.forum.module.banner.vo.BannerVO;
import com.laimengchao.sso.forum.module.forum.entity.ForumExchange;
import com.laimengchao.sso.forum.module.forum.entity.ForumGift;
import com.laimengchao.sso.forum.module.lottery.dto.LotteryConfigRequest;
import com.laimengchao.sso.forum.module.lottery.dto.PrizeRequest;
import com.laimengchao.sso.forum.module.lottery.service.LotteryService;
import com.laimengchao.sso.forum.module.lottery.vo.LotteryConfigVO;
import com.laimengchao.sso.forum.module.lottery.vo.LotteryPrizeVO;
import com.laimengchao.sso.forum.module.report.dto.ReportHandleRequest;
import com.laimengchao.sso.forum.module.report.service.ReportService;
import com.laimengchao.sso.forum.module.report.vo.ReportVO;
import com.laimengchao.sso.forum.module.site.dto.FriendLinkRequest;
import com.laimengchao.sso.forum.module.site.dto.SiteConfigRequest;
import com.laimengchao.sso.forum.module.site.entity.SiteConfig;
import com.laimengchao.sso.forum.module.site.service.SiteService;
import com.laimengchao.sso.forum.module.site.vo.FriendLinkVO;
import com.laimengchao.sso.forum.module.taxonomy.dto.BoardRequest;
import com.laimengchao.sso.forum.module.taxonomy.dto.TagCreateRequest;
import com.laimengchao.sso.forum.module.taxonomy.dto.TagUpdateRequest;
import com.laimengchao.sso.forum.module.taxonomy.service.TaxonomyService;
import com.laimengchao.sso.forum.module.taxonomy.vo.BoardVO;
import com.laimengchao.sso.forum.module.taxonomy.vo.TagVO;
import com.laimengchao.sso.forum.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/forum/admin")
@RequiredArgsConstructor
public class AdminForumController {

    private final AdminService adminService;
    private final TaxonomyService taxonomyService;
    private final ReportService reportService;
    private final SiteService siteService;
    private final BannerService bannerService;
    private final LotteryService lotteryService;

    @Permit(PermissionCodes.FORUM_SITE_VIEW)
    @GetMapping("/banners")
    public R<List<BannerVO>> banners() {
        return R.ok(bannerService.adminBanners());
    }

    @Permit(PermissionCodes.FORUM_SITE_EDIT)
    @PostMapping("/banners")
    public R<BannerVO> createBanner(@RequestBody BannerRequest body) {
        return R.ok(bannerService.create(body));
    }

    @Permit(PermissionCodes.FORUM_SITE_EDIT)
    @PostMapping(value = "/banner-images", consumes = "multipart/form-data")
    public R<AttachmentVO> uploadBannerImage(@RequestParam("file") MultipartFile file) {
        return R.ok(bannerService.uploadImage(file));
    }

    @Permit(PermissionCodes.FORUM_SITE_EDIT)
    @PatchMapping("/banners/{id}")
    public R<BannerVO> updateBanner(@PathVariable long id, @RequestBody BannerRequest body) {
        return R.ok(bannerService.update(id, body));
    }

    @Permit(PermissionCodes.FORUM_SITE_VIEW)
    @GetMapping("/site")
    public R<SiteConfig> site() {
        return R.ok(siteService.adminConfig());
    }

    @Permit(PermissionCodes.FORUM_SITE_EDIT)
    @PatchMapping("/site")
    public R<SiteConfig> updateSite(@RequestBody SiteConfigRequest body) {
        return R.ok(siteService.updateConfig(body));
    }

    @Permit(PermissionCodes.FORUM_SITE_VIEW)
    @GetMapping("/friend-links")
    public R<List<FriendLinkVO>> friendLinks() {
        return R.ok(siteService.adminLinks());
    }

    @Permit(PermissionCodes.FORUM_SITE_LINK_MANAGE)
    @PostMapping("/friend-links")
    public R<FriendLinkVO> createFriendLink(@RequestBody FriendLinkRequest body) {
        return R.ok(siteService.createLink(body));
    }

    @Permit(PermissionCodes.FORUM_SITE_LINK_MANAGE)
    @PatchMapping("/friend-links/{id}")
    public R<FriendLinkVO> updateFriendLink(@PathVariable long id, @RequestBody FriendLinkRequest body) {
        return R.ok(siteService.updateLink(id, body));
    }

    @Permit(PermissionCodes.FORUM_TAXONOMY_VIEW)
    @GetMapping("/boards")
    public R<List<BoardVO>> boards() {
        return R.ok(taxonomyService.adminBoards());
    }

    @Permit(PermissionCodes.FORUM_TAXONOMY_BOARD_MANAGE)
    @PostMapping("/boards")
    public R<BoardVO> createBoard(@RequestBody BoardRequest body) {
        return R.ok(taxonomyService.createBoard(body));
    }

    @Permit(PermissionCodes.FORUM_TAXONOMY_BOARD_MANAGE)
    @PatchMapping("/boards/{key}")
    public R<BoardVO> updateBoard(@PathVariable String key, @RequestBody BoardRequest body) {
        return R.ok(taxonomyService.updateBoard(key, body));
    }

    @Permit(PermissionCodes.FORUM_TAXONOMY_VIEW)
    @GetMapping("/tags")
    public R<List<TagVO>> tags(@RequestParam(required = false) String board) {
        return R.ok(taxonomyService.adminTags(board));
    }

    @Permit(PermissionCodes.FORUM_TAXONOMY_TAG_MANAGE)
    @PostMapping("/tags")
    public R<TagVO> createTag(@RequestBody TagCreateRequest body) {
        return R.ok(taxonomyService.createTag(body));
    }

    @Permit(PermissionCodes.FORUM_TAXONOMY_TAG_MANAGE)
    @PatchMapping("/tags/{id}")
    public R<TagVO> updateTag(@PathVariable long id, @RequestBody TagUpdateRequest body) {
        return R.ok(taxonomyService.updateTag(id, body));
    }

    @Permit(PermissionCodes.FORUM_CONTENT_VIEW)
    @GetMapping("/threads")
    public R<List<AdminThreadVO>> threads(@RequestParam(required = false) String kind,
                                          @RequestParam(required = false) String board,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Boolean deleted) {
        return R.ok(adminService.threads(kind, board, keyword, deleted));
    }

    /** 内容分页查询，管理端列表走此接口，列表不查正文，避免一次拉取全部帖子。 */
    @Permit(PermissionCodes.FORUM_CONTENT_VIEW)
    @GetMapping("/threads/page")
    public R<AdminThreadPageVO> threadsPage(@RequestParam(required = false) String kind,
                                            @RequestParam(required = false) String board,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Boolean deleted,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return R.ok(adminService.threadsPage(kind, board, keyword, deleted, page, size));
    }

    /**
     * 一个端点承载置顶/加精/已解决/删除四种状态变更，注解只能声明「任一即可」，
     * 具体字段对应的权限码由 {@link AdminService#updateThread} 精确判定。
     */
    @Permit({PermissionCodes.FORUM_CONTENT_DELETE, PermissionCodes.FORUM_CONTENT_PIN, PermissionCodes.FORUM_CONTENT_SOLVE, PermissionCodes.FORUM_CONTENT_RECOMMEND})
    @PatchMapping("/threads/{id}")
    public R<AdminThreadVO> updateThread(@PathVariable long id, @RequestBody ThreadStatusUpdateRequest body) {
        return R.ok(adminService.updateThread(id, body));
    }

    @Permit(PermissionCodes.FORUM_COMMENT_VIEW)
    @GetMapping("/comments")
    public R<List<AdminCommentVO>> comments(@RequestParam(required = false) Long threadId,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Boolean deleted) {
        return R.ok(adminService.comments(threadId, keyword, deleted));
    }

    /** 评论分页查询，管理端列表走此接口，避免一次拉取全部评论正文。 */
    @Permit(PermissionCodes.FORUM_COMMENT_VIEW)
    @GetMapping("/comments/page")
    public R<AdminCommentPageVO> commentsPage(@RequestParam(required = false) Long threadId,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Boolean deleted,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return R.ok(adminService.commentsPage(threadId, keyword, deleted, page, size));
    }

    @Permit(PermissionCodes.FORUM_COMMENT_DELETE)
    @PatchMapping("/comments/{id}")
    public R<AdminCommentVO> deleteComment(@PathVariable long id, @RequestBody CommentDeleteRequest body) {
        return R.ok(adminService.setCommentDeleted(id, body));
    }

    @Permit(PermissionCodes.FORUM_SENSITIVE_VIEW)
    @GetMapping("/sensitive-words")
    public R<List<SensitiveWordVO>> sensitiveWords() {
        return R.ok(adminService.sensitiveWords());
    }

    /** 敏感词分页查询，管理端列表走此接口，避免一次拉取全部敏感词。 */
    @Permit(PermissionCodes.FORUM_SENSITIVE_VIEW)
    @GetMapping("/sensitive-words/page")
    public R<SensitiveWordPageVO> sensitiveWordsPage(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return R.ok(adminService.sensitiveWordsPage(page, size));
    }

    @Permit(PermissionCodes.FORUM_SENSITIVE_ADD)
    @PostMapping("/sensitive-words")
    public R<SensitiveWordVO> addSensitiveWord(@RequestBody SensitiveWordRequest body) {
        return R.ok(adminService.addSensitiveWord(body));
    }

    @Permit(PermissionCodes.FORUM_SENSITIVE_TOGGLE)
    @PatchMapping("/sensitive-words/{id}")
    public R<Void> enableSensitiveWord(@PathVariable long id, @RequestBody SensitiveWordEnabledRequest body) {
        adminService.setSensitiveWordEnabled(id, body);
        return R.ok();
    }

    @Permit(PermissionCodes.FORUM_SENSITIVE_DELETE)
    @DeleteMapping("/sensitive-words/{id}")
    public R<Void> deleteSensitiveWord(@PathVariable long id) {
        adminService.deleteSensitiveWord(id);
        return R.ok();
    }

    @Permit(PermissionCodes.FORUM_GIFT_VIEW)
    @GetMapping("/gifts")
    public R<List<ForumGift>> gifts() {
        return R.ok(adminService.adminGifts());
    }

    @Permit(PermissionCodes.FORUM_GIFT_EDIT)
    @PostMapping("/gifts")
    public R<ForumGift> createGift(@RequestBody GiftRequest body) {
        return R.ok(adminService.createGift(body));
    }

    @Permit(PermissionCodes.FORUM_GIFT_EDIT)
    @PatchMapping("/gifts/{id}")
    public R<ForumGift> updateGift(@PathVariable long id, @RequestBody GiftRequest body) {
        return R.ok(adminService.updateGift(id, body));
    }

    @Permit(PermissionCodes.FORUM_REPORT_VIEW)
    @GetMapping("/reports")
    public R<List<ReportVO>> reports(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) String targetType) {
        return R.ok(reportService.list(status, targetType));
    }

    @Permit(PermissionCodes.FORUM_REPORT_PROCESS)
    @PostMapping("/reports/{id}/process")
    public R<ReportVO> handleReport(@PathVariable long id, @RequestBody ReportHandleRequest body) {
        return R.ok(reportService.handle(id, body));
    }

    @Permit(PermissionCodes.FORUM_EXCHANGE_VIEW)
    @GetMapping("/exchanges")
    public R<List<ForumExchange>> exchanges(@RequestParam(required = false) String status) {
        return R.ok(adminService.exchanges(status));
    }

    @Permit(PermissionCodes.FORUM_EXCHANGE_SHIP)
    @PostMapping("/exchanges/{id}/ship")
    public R<ForumExchange> shipExchange(@PathVariable long id, @RequestBody ExchangeFulfillmentRequest body) {
        return R.ok(adminService.shipExchange(id, body));
    }

    @Permit(PermissionCodes.FORUM_EXCHANGE_ISSUE)
    @PostMapping("/exchanges/{id}/issue")
    public R<ForumExchange> issueExchange(@PathVariable long id, @RequestBody ExchangeFulfillmentRequest body) {
        return R.ok(adminService.issueExchange(id, body));
    }

    @Permit(PermissionCodes.FORUM_LOTTERY_CONFIG_VIEW)
    @GetMapping("/lottery")
    public R<LotteryConfigVO> lottery() {
        return R.ok(lotteryService.adminConfig());
    }

    @Permit(PermissionCodes.FORUM_LOTTERY_CONFIG_EDIT)
    @PatchMapping("/lottery/config")
    public R<LotteryConfigVO> updateLotteryConfig(@RequestBody LotteryConfigRequest body) {
        return R.ok(lotteryService.updateConfig(body));
    }

    @Permit(PermissionCodes.FORUM_LOTTERY_CONFIG_EDIT)
    @PostMapping("/lottery/prizes")
    public R<LotteryPrizeVO> createPrize(@RequestBody PrizeRequest body) {
        return R.ok(lotteryService.createPrize(body));
    }

    @Permit(PermissionCodes.FORUM_LOTTERY_CONFIG_EDIT)
    @PatchMapping("/lottery/prizes/{id}")
    public R<LotteryPrizeVO> updatePrize(@PathVariable long id, @RequestBody PrizeRequest body) {
        return R.ok(lotteryService.updatePrize(id, body));
    }

    @Permit(PermissionCodes.FORUM_LOTTERY_CONFIG_EDIT)
    @DeleteMapping("/lottery/prizes/{id}")
    public R<Void> deletePrize(@PathVariable long id) {
        lotteryService.deletePrize(id);
        return R.ok();
    }

    /** 按现有比例把启用奖项权重归一化到合计 100 */
    @Permit(PermissionCodes.FORUM_LOTTERY_CONFIG_EDIT)
    @PostMapping("/lottery/prizes/normalize")
    public R<LotteryConfigVO> normalizeLotteryWeights() {
        lotteryService.normalizeWeights();
        return R.ok(lotteryService.adminConfig());
    }
}
