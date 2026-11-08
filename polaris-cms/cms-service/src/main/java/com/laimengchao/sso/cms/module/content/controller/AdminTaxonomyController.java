package com.laimengchao.sso.cms.module.content.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.cms.module.content.dto.CategorySaveRequest;
import com.laimengchao.sso.cms.module.content.dto.TagVO;
import com.laimengchao.sso.cms.module.content.service.CategoryService;
import com.laimengchao.sso.cms.module.content.service.TagService;
import com.laimengchao.sso.cms.security.Permit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 分类与标签管理 */
@RestController
@RequestMapping("/api/cms")
@RequiredArgsConstructor
public class AdminTaxonomyController {

    private final CategoryService categoryService;
    private final TagService tagService;

    @GetMapping("/categories")
    @Permit("category:view")
    public R<List<TagVO>> categories() {
        return R.ok(categoryService.listAll());
    }

    @PostMapping("/categories")
    @Permit("category:manage")
    public R<Void> createCategory(@Valid @RequestBody CategorySaveRequest req) {
        categoryService.save(req);
        return R.ok();
    }

    @PutMapping("/categories/{id}")
    @Permit("category:manage")
    public R<Void> updateCategory(@PathVariable Long id, @Valid @RequestBody CategorySaveRequest req) {
        req.setId(id);
        categoryService.save(req);
        return R.ok();
    }

    @DeleteMapping("/categories/{id}")
    @Permit("category:manage")
    public R<Void> deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        return R.ok();
    }

    @GetMapping("/tags")
    @Permit("tag:view")
    public R<List<TagVO>> tags(@RequestParam(required = false) String keyword) {
        return R.ok(tagService.listAll(keyword));
    }

    @PostMapping("/tags")
    @Permit("tag:manage")
    public R<TagVO> createTag(@RequestBody Map<String, String> body) {
        return R.ok(tagService.create(body.get("name")));
    }

    @PutMapping("/tags/{id}")
    @Permit("tag:manage")
    public R<Void> renameTag(@PathVariable Long id, @RequestBody Map<String, String> body) {
        tagService.rename(id, body.get("name"));
        return R.ok();
    }

    @DeleteMapping("/tags/{id}")
    @Permit("tag:manage")
    public R<Void> deleteTag(@PathVariable Long id) {
        tagService.delete(id);
        return R.ok();
    }
}
