package com.laimengchao.sso.blog.module.article.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.blog.common.log.OpLog;
import com.laimengchao.sso.blog.module.article.dto.TagSaveRequest;
import com.laimengchao.sso.blog.module.article.dto.TagVO;
import com.laimengchao.sso.blog.module.article.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.laimengchao.sso.blog.security.Permit;
import com.laimengchao.sso.blog.constant.PermissionCodes;

/**
 * 后台标签管理。
 */
@RestController
@RequestMapping("/api/blog/admin/tags")
@RequiredArgsConstructor
@Tag(name = "后台-标签", description = "标签 CRUD 与合并")
public class AdminTagController {

    private final TagService tagService;

    @GetMapping
    @Operation(summary = "标签列表（含文章数）")
    @Permit(PermissionCodes.TAG_LIST)
    public R<List<TagVO>> list() {
        return R.ok(tagService.listWithCount());
    }

    @PostMapping
    @OpLog(module = "tag", action = "create")
    @Operation(summary = "新增标签")
    @Permit(PermissionCodes.TAG_EDIT)
    public R<Long> create(@RequestBody TagSaveRequest request) {
        request.setId(null);
        return R.ok(tagService.save(request));
    }

    @PutMapping("/{id}")
    @OpLog(module = "tag", action = "update")
    @Operation(summary = "修改标签")
    @Permit(PermissionCodes.TAG_EDIT)
    public R<Long> update(@PathVariable Long id, @RequestBody TagSaveRequest request) {
        request.setId(id);
        return R.ok(tagService.save(request));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "tag", action = "delete")
    @Operation(summary = "删除标签")
    @Permit(PermissionCodes.TAG_DELETE)
    public R<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return R.ok();
    }

    @PostMapping("/merge")
    @OpLog(module = "tag", action = "merge")
    @Operation(summary = "合并标签", description = "sourceId 下的文章全部迁移到 targetId，随后删除 sourceId")
    @Permit(PermissionCodes.TAG_EDIT)
    public R<Void> merge(@RequestBody MergeRequest request) {
        tagService.merge(request.getSourceId(), request.getTargetId());
        return R.ok();
    }

    @Data
    public static class MergeRequest {
        private Long sourceId;
        private Long targetId;
    }
}
