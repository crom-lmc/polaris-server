package com.laimengchao.sso.cms.module.media.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.cms.module.media.entity.Media;
import com.laimengchao.sso.cms.module.media.service.MediaService;
import com.laimengchao.sso.cms.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 媒体库管理 */
@RestController
@RequestMapping("/api/cms/media")
@RequiredArgsConstructor
public class AdminMediaController {

    private final MediaService mediaService;

    @PostMapping("/upload")
    @Permit("media:upload")
    public R<Media> upload(@RequestParam("file") MultipartFile file) {
        return R.ok(mediaService.upload(file));
    }

    @GetMapping
    @Permit("media:view")
    public R<R.Page<Media>> page(@RequestParam(required = false) String keyword,
                                 @RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "12") int size) {
        IPage<Media> p = mediaService.page(keyword, page, size);
        return R.ok(R.Page.of(p.getRecords(), p.getTotal()));
    }

    @DeleteMapping("/{id}")
    @Permit("media:delete")
    public R<Void> delete(@PathVariable Long id) {
        mediaService.delete(id);
        return R.ok();
    }
}
