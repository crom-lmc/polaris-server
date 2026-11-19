package com.laimengchao.sso.forum.module.admin.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.forum.module.admin.dto.CommentDeleteRequest;
import com.laimengchao.sso.forum.module.admin.dto.ExchangeFulfillmentRequest;
import com.laimengchao.sso.forum.module.admin.dto.SensitiveWordEnabledRequest;
import com.laimengchao.sso.forum.module.admin.dto.SensitiveWordRequest;
import com.laimengchao.sso.forum.module.admin.dto.ThreadStatusUpdateRequest;
import com.laimengchao.sso.forum.module.admin.service.AdminService;
import com.laimengchao.sso.forum.module.admin.vo.AdminCommentVO;
import com.laimengchao.sso.forum.module.admin.vo.AdminThreadVO;
import com.laimengchao.sso.forum.module.admin.vo.SensitiveWordVO;
import com.laimengchao.sso.forum.module.forum.entity.ForumExchange;
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

import java.util.List;

@RestController
@RequestMapping("/api/forum/admin")
@RequiredArgsConstructor
public class AdminForumController {

    private final AdminService adminService;
    private final TaxonomyService taxonomyService;
    private final ReportService reportService;
    private final SiteService siteService;

    @Permit("forum:site:view")
    @GetMapping("/site")
    public R<SiteConfig> site() {
        return R.ok(siteService.adminConfig());
    }

    @Permit("forum:site:edit")
    @PatchMapping("/site")
    public R<SiteConfig> updateSite(@RequestBody SiteConfigRequest body) {
        return R.ok(siteService.updateConfig(body));
    }

    @Permit("forum:site:view")
    @GetMapping("/friend-links")
    public R<List<FriendLinkVO>> friendLinks() {
        return R.ok(siteService.adminLinks());
    }

    @Permit("forum:site:link:manage")
    @PostMapping("/friend-links")
    public R<FriendLinkVO> createFriendLink(@RequestBody FriendLinkRequest body) {
        return R.ok(siteService.createLink(body));
    }

    @Permit("forum:site:link:manage")
    @PatchMapping("/friend-links/{id}")
    public R<FriendLinkVO> updateFriendLink(@PathVariable long id, @RequestBody FriendLinkRequest body) {
        return R.ok(siteService.updateLink(id, body));
    }

    @Permit("forum:taxonomy:view")
    @GetMapping("/boards")
    public R<List<BoardVO>> boards() {
        return R.ok(taxonomyService.adminBoards());
    }

    @Permit("forum:taxonomy:board:manage")
    @PostMapping("/boards")
    public R<BoardVO> createBoard(@RequestBody BoardRequest body) {
        return R.ok(taxonomyService.createBoard(body));
    }

    @Permit("forum:taxonomy:board:manage")
    @PatchMapping("/boards/{key}")
    public R<BoardVO> updateBoard(@PathVariable String key, @RequestBody BoardRequest body) {
        return R.ok(taxonomyService.updateBoard(key, body));
    }

    @Permit("forum:taxonomy:view")
    @GetMapping("/tags")
    public R<List<TagVO>> tags(@RequestParam(required = false) String board) {
        return R.ok(taxonomyService.adminTags(board));
    }

    @Permit("forum:taxonomy:tag:manage")
    @PostMapping("/tags")
    public R<TagVO> createTag(@RequestBody TagCreateRequest body) {
        return R.ok(taxonomyService.createTag(body));
    }

    @Permit("forum:taxonomy:tag:manage")
    @PatchMapping("/tags/{id}")
    public R<TagVO> updateTag(@PathVariable long id, @RequestBody TagUpdateRequest body) {
        return R.ok(taxonomyService.updateTag(id, body));
    }

    @Permit("forum:content:view")
    @GetMapping("/threads")
    public R<List<AdminThreadVO>> threads(@RequestParam(required = false) String kind,
                                          @RequestParam(required = false) String board,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Boolean deleted) {
        return R.ok(adminService.threads(kind, board, keyword, deleted));
    }

    /**
     * 一个端点承载置顶/加精/已解决/删除四种状态变更，注解只能声明「任一即可」，
     * 具体字段对应的权限码由 {@link AdminService#updateThread} 精确判定。
     */
    @Permit({"forum:content:delete", "forum:content:pin", "forum:content:solve", "forum:content:recommend"})
    @PatchMapping("/threads/{id}")
    public R<AdminThreadVO> updateThread(@PathVariable long id, @RequestBody ThreadStatusUpdateRequest body) {
        return R.ok(adminService.updateThread(id, body));
    }

    @Permit("forum:comment:view")
    @GetMapping("/comments")
    public R<List<AdminCommentVO>> comments(@RequestParam(required = false) Long threadId,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Boolean deleted) {
        return R.ok(adminService.comments(threadId, keyword, deleted));
    }

    @Permit("forum:comment:delete")
    @PatchMapping("/comments/{id}")
    public R<AdminCommentVO> deleteComment(@PathVariable long id, @RequestBody CommentDeleteRequest body) {
        return R.ok(adminService.setCommentDeleted(id, body));
    }

    @Permit("forum:sensitive:view")
    @GetMapping("/sensitive-words")
    public R<List<SensitiveWordVO>> sensitiveWords() {
        return R.ok(adminService.sensitiveWords());
    }

    @Permit("forum:sensitive:add")
    @PostMapping("/sensitive-words")
    public R<SensitiveWordVO> addSensitiveWord(@RequestBody SensitiveWordRequest body) {
        return R.ok(adminService.addSensitiveWord(body));
    }

    @Permit("forum:sensitive:toggle")
    @PatchMapping("/sensitive-words/{id}")
    public R<Void> enableSensitiveWord(@PathVariable long id, @RequestBody SensitiveWordEnabledRequest body) {
        adminService.setSensitiveWordEnabled(id, body);
        return R.ok();
    }

    @Permit("forum:sensitive:delete")
    @DeleteMapping("/sensitive-words/{id}")
    public R<Void> deleteSensitiveWord(@PathVariable long id) {
        adminService.deleteSensitiveWord(id);
        return R.ok();
    }

    @Permit("forum:report:view")
    @GetMapping("/reports")
    public R<List<ReportVO>> reports(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) String targetType) {
        return R.ok(reportService.list(status, targetType));
    }

    @Permit("forum:report:process")
    @PostMapping("/reports/{id}/process")
    public R<ReportVO> handleReport(@PathVariable long id, @RequestBody ReportHandleRequest body) {
        return R.ok(reportService.handle(id, body));
    }

    @Permit("forum:exchange:view")
    @GetMapping("/exchanges")
    public R<List<ForumExchange>> exchanges(@RequestParam(required = false) String status) {
        return R.ok(adminService.exchanges(status));
    }

    @Permit("forum:exchange:ship")
    @PostMapping("/exchanges/{id}/ship")
    public R<ForumExchange> shipExchange(@PathVariable long id, @RequestBody ExchangeFulfillmentRequest body) {
        return R.ok(adminService.shipExchange(id, body));
    }

    @Permit("forum:exchange:issue")
    @PostMapping("/exchanges/{id}/issue")
    public R<ForumExchange> issueExchange(@PathVariable long id, @RequestBody ExchangeFulfillmentRequest body) {
        return R.ok(adminService.issueExchange(id, body));
    }
}
