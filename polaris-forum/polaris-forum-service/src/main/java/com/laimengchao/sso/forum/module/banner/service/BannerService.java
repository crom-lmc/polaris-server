package com.laimengchao.sso.forum.module.banner.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.forum.module.attachment.service.AttachmentService;
import com.laimengchao.sso.forum.module.attachment.vo.AttachmentVO;
import com.laimengchao.sso.forum.module.banner.dto.BannerRequest;
import com.laimengchao.sso.forum.module.banner.entity.Banner;
import com.laimengchao.sso.forum.module.banner.mapper.BannerMapper;
import com.laimengchao.sso.forum.module.banner.vo.BannerVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BannerService {

    private final BannerMapper mapper;
    private final AttachmentService attachmentService;

    /** 管理端上传轮播图片：以 COVER 用途存入论坛附件，返回可被公开地址引用的附件信息。 */
    public AttachmentVO uploadImage(MultipartFile file) {
        return attachmentService.upload(file, "COVER");
    }

    public List<BannerVO> publicBanners() {
        return mapper.listBanners(true).stream().map(this::view).toList();
    }

    public List<BannerVO> adminBanners() {
        return mapper.listBanners(false).stream().map(this::view).toList();
    }

    public BannerVO create(BannerRequest body) {
        long id = IdWorker.getId();
        Banner values = new Banner();
        values.setId(id);
        values.setTitle(title(body.getTitle()));
        values.setImageAttachmentId(attachmentId(body.getImageAttachmentId()));
        values.setLinkUrl(linkUrl(body.getLinkUrl()));
        values.setSortNo(intValue(body.getSortNo()));
        values.setEnabled(body.isEnabledPresent() ? bool(body.getEnabled()) : true);
        mapper.insertBanner(values);
        return view(require(id));
    }

    public BannerVO update(long id, BannerRequest body) {
        Banner current = require(id);
        Banner values = new Banner();
        values.setId(id);
        values.setTitle(body.isTitlePresent() ? title(body.getTitle()) : current.getTitle());
        values.setImageAttachmentId(body.isImageAttachmentIdPresent()
                ? attachmentId(body.getImageAttachmentId()) : current.getImageAttachmentId());
        values.setLinkUrl(body.isLinkUrlPresent() ? linkUrl(body.getLinkUrl()) : current.getLinkUrl());
        values.setSortNo(body.isSortNoPresent() ? intValue(body.getSortNo()) : intValue(current.getSortNo()));
        values.setEnabled(body.isEnabledPresent() ? bool(body.getEnabled()) : Boolean.TRUE.equals(current.getEnabled()));
        mapper.updateBanner(values);
        return view(require(id));
    }

    private Banner require(long id) {
        Banner banner = mapper.findBanner(id);
        if (banner == null) throw new BizException(ErrorCode.NOT_FOUND, "轮播图不存在");
        return banner;
    }

    private BannerVO view(Banner row) {
        BannerVO view = new BannerVO();
        view.setId(row.getId());
        view.setTitle(row.getTitle());
        Long attachmentId = row.getImageAttachmentId();
        view.setImageUrl(attachmentId != null && attachmentId > 0
                ? "/api/forum/public/attachments/" + attachmentId : null);
        view.setLinkUrl(row.getLinkUrl());
        view.setSortNo(row.getSortNo());
        view.setEnabled(Boolean.TRUE.equals(row.getEnabled()));
        view.setCreatedAt(row.getCreatedAt());
        view.setUpdatedAt(row.getUpdatedAt());
        return view;
    }

    private String title(Object raw) {
        String value = text(raw).trim();
        if (value.length() > 120) throw param("标题长度不能超过 120 个字符");
        return value;
    }

    private long attachmentId(Object raw) {
        if (raw instanceof Number number) {
            long id = number.longValue();
            if (id > 0) return id;
        }
        try {
            long id = Long.parseLong(text(raw).trim());
            if (id > 0) return id;
        } catch (NumberFormatException ignored) {
        }
        throw param("请先上传轮播图片");
    }

    private String linkUrl(Object raw) {
        String value = text(raw).trim();
        if (value.length() > 500) throw param("跳转地址长度不能超过 500 个字符");
        if (value.isEmpty()) return "";
        // 站内相对路径（以 / 开头）直接放行
        if (value.startsWith("/")) return value;
        // 外链仅允许有效的 http/https 地址
        try {
            URI uri = URI.create(value);
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null) {
                throw param("跳转地址仅支持 http/https 或站内路径");
            }
        } catch (IllegalArgumentException e) {
            throw param("跳转地址不是有效的地址");
        }
        return value;
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        if ("true".equalsIgnoreCase(text(value)) || "false".equalsIgnoreCase(text(value))) {
            return Boolean.parseBoolean(text(value));
        }
        throw param("enabled 必须为布尔值");
    }

    private BizException param(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null || String.valueOf(value).isBlank() ? 0 : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException e) { return 0; }
    }
}
