package com.laimengchao.sso.cms.module.media.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.cms.common.CmsErrorCode;
import com.laimengchao.sso.cms.module.media.entity.Media;
import com.laimengchao.sso.cms.module.media.mapper.MediaMapper;
import com.laimengchao.sso.cms.security.SecurityUtils;
import com.laimengchao.sso.cms.storage.StorageService;
import com.laimengchao.sso.cms.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** 媒体库：上传/分页/删除（软删 + 磁盘清理） */
@Service
@RequiredArgsConstructor
public class MediaService {

    private final MediaMapper mediaMapper;
    private final StorageService storageService;

    public Media upload(MultipartFile file) {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        StoredFile sf;
        try {
            sf = storageService.store(file, null);
        } catch (IOException e) {
            throw new BizException(CmsErrorCode.UPLOAD_FAILED, "文件写入失败：" + e.getMessage());
        }
        Media m = new Media();
        m.setFilename(sf.getFilename());
        m.setOriginalName(sf.getOriginalName());
        m.setUrl(sf.getUrl());
        m.setContentType(sf.getContentType());
        m.setSize(sf.getSize());
        m.setUploadedBy(uid);
        m.setUploadedName(SecurityUtils.currentUsername().orElse(""));
        mediaMapper.insert(m);
        return m;
    }

    public IPage<Media> page(String keyword, int page, int size) {
        LambdaQueryWrapper<Media> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.like(Media::getOriginalName, keyword.trim());
        }
        w.orderByDesc(Media::getId);
        return mediaMapper.selectPage(Page.of(page, size), w);
    }

    public void delete(Long id) {
        Media m = mediaMapper.selectById(id);
        if (m == null) {
            throw new BizException(CmsErrorCode.DATA_NOT_FOUND, "文件不存在");
        }
        storageService.delete(m.getUrl());
        mediaMapper.deleteById(id);
    }
}
