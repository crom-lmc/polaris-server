package com.laimengchao.sso.cms.module.content.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.cms.module.content.dto.ArticleQuery;
import com.laimengchao.sso.cms.module.content.dto.ArticleSaveRequest;
import com.laimengchao.sso.cms.module.content.dto.ArticleVO;
import com.laimengchao.sso.cms.module.content.dto.VersionVO;
import com.laimengchao.sso.cms.module.content.service.ArticleService;
import com.laimengchao.sso.cms.security.Permit;
import com.laimengchao.sso.common.security.PermissionCodes;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** CMS 内容管理接口（权限码在统一账号中心维护，应用 code = cms） */
@RestController
@RequestMapping("/api/cms/articles")
@RequiredArgsConstructor
public class AdminArticleController {

    private final ArticleService articleService;

    @GetMapping
    @Permit(PermissionCodes.ARTICLE_VIEW)
    public R<R.Page<ArticleVO>> page(ArticleQuery query) {
        return R.ok(articleService.page(query));
    }

    @GetMapping("/{id}")
    @Permit(PermissionCodes.ARTICLE_VIEW)
    public R<ArticleVO> detail(@PathVariable Long id) {
        return R.ok(articleService.detail(id));
    }

    @PostMapping
    @Permit(PermissionCodes.ARTICLE_CREATE)
    public R<ArticleVO> create(@Valid @RequestBody ArticleSaveRequest req) {
        req.setId(null);
        return R.ok(articleService.save(req));
    }

    @PutMapping("/{id}")
    @Permit(PermissionCodes.ARTICLE_EDIT)
    public R<ArticleVO> update(@PathVariable Long id, @Valid @RequestBody ArticleSaveRequest req) {
        req.setId(id);
        return R.ok(articleService.save(req));
    }

    /** 提审（作者本人）：置「6 审批中」并发起 cms.article_publish 审批 */
    @PostMapping("/{id}/submit")
    @Permit({PermissionCodes.ARTICLE_CREATE, PermissionCodes.ARTICLE_EDIT})
    public R<Void> submit(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        articleService.submit(id, body == null ? "" : body.getOrDefault("note", ""));
        return R.ok();
    }

    /** 撤回审批中的稿件（作者本人 / 超管） */
    @PostMapping("/{id}/withdraw")
    @Permit(PermissionCodes.ARTICLE_EDIT)
    public R<Void> withdraw(@PathVariable Long id) {
        articleService.withdraw(id);
        return R.ok();
    }

    /** 审核通过 */
    @PostMapping("/{id}/approve")
    @Permit(PermissionCodes.ARTICLE_AUDIT)
    public R<Void> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        articleService.approve(id, body == null ? "" : body.getOrDefault("note", ""));
        return R.ok();
    }

    /** 驳回 */
    @PostMapping("/{id}/reject")
    @Permit(PermissionCodes.ARTICLE_AUDIT)
    public R<Void> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        articleService.reject(id, body == null ? "" : body.getOrDefault("note", ""));
        return R.ok();
    }

    /** 发布（仅审批通过、待发布的稿件；草稿/驳回/审批中会被引擎拒绝） */
    @PostMapping("/{id}/publish")
    @Permit(PermissionCodes.ARTICLE_PUBLISH)
    public R<Void> publish(@PathVariable Long id) {
        articleService.publishNow(id);
        return R.ok();
    }

    /** 下线 */
    @PostMapping("/{id}/offline")
    @Permit(PermissionCodes.ARTICLE_PUBLISH)
    public R<Void> offline(@PathVariable Long id) {
        articleService.offline(id);
        return R.ok();
    }

    /** 置顶 / 推荐（flag = pin | recommend） */
    @PatchMapping("/{id}/flag")
    @Permit(PermissionCodes.ARTICLE_PIN)
    public R<Void> flag(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String flag = String.valueOf(body.getOrDefault("flag", ""));
        boolean on = Boolean.TRUE.equals(body.get("on"))
                || "1".equals(String.valueOf(body.get("on")));
        articleService.setFlag(id, flag, on);
        return R.ok();
    }

    /** 移入回收站 */
    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.ARTICLE_DELETE)
    public R<Void> recycle(@PathVariable Long id) {
        articleService.toRecycle(id);
        return R.ok();
    }

    /** 从回收站恢复 */
    @PostMapping("/{id}/restore")
    @Permit(PermissionCodes.ARTICLE_DELETE)
    public R<Void> restore(@PathVariable Long id) {
        articleService.restore(id);
        return R.ok();
    }

    /** 彻底删除（不可恢复） */
    @DeleteMapping("/{id}/forever")
    @Permit(PermissionCodes.ARTICLE_DELETE)
    public R<Void> deleteForever(@PathVariable Long id) {
        articleService.deleteForever(id);
        return R.ok();
    }

    /** 版本列表 */
    @GetMapping("/{id}/versions")
    @Permit(PermissionCodes.ARTICLE_VERSION)
    public R<List<VersionVO>> versions(@PathVariable Long id) {
        return R.ok(articleService.versions(id));
    }

    /** 一键回滚 */
    @PostMapping("/{id}/rollback/{versionId}")
    @Permit(PermissionCodes.ARTICLE_ROLLBACK)
    public R<Void> rollback(@PathVariable Long id, @PathVariable Long versionId) {
        articleService.rollback(id, versionId);
        return R.ok();
    }
}
