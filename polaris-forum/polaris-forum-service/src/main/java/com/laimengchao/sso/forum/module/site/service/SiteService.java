package com.laimengchao.sso.forum.module.site.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.forum.module.site.dto.FriendLinkRequest;
import com.laimengchao.sso.forum.module.site.dto.SiteConfigRequest;
import com.laimengchao.sso.forum.module.site.entity.FriendLink;
import com.laimengchao.sso.forum.module.site.entity.SiteConfig;
import com.laimengchao.sso.forum.module.site.mapper.SiteMapper;
import com.laimengchao.sso.forum.module.site.vo.FriendLinkVO;
import com.laimengchao.sso.forum.module.site.vo.PublicSiteConfigVO;
import com.laimengchao.sso.forum.security.ForumAdminGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SiteService {

    private final SiteMapper mapper;
    private final ForumAdminGuard guard;

    public PublicSiteConfigVO publicConfig() {
        SiteConfig config = config();
        PublicSiteConfigVO view = new PublicSiteConfigVO();
        view.setAboutContent(config.getAboutContent());
        view.setFooterText(config.getFooterText());
        view.setFriendLinks(publicLinks());
        return view;
    }

    public List<FriendLinkVO> publicLinks() {
        return mapper.listLinks(true).stream().map(this::linkView).toList();
    }

    public SiteConfig adminConfig() {
        guard.require("forum:site");
        return config();
    }

    public SiteConfig updateConfig(SiteConfigRequest body) {
        guard.require("forum:site");
        SiteConfig current = config();
        SiteConfig values = new SiteConfig();
        values.setAboutContent(value(body.isAboutContentPresent() ? body.getAboutContent() : current.getAboutContent(),
                "aboutContent", 50000));
        values.setFooterText(value(body.isFooterTextPresent() ? body.getFooterText() : current.getFooterText(),
                "footerText", 500));
        mapper.updateConfig(values);
        return config();
    }

    public List<FriendLinkVO> adminLinks() {
        guard.require("forum:site");
        return mapper.listLinks(false).stream().map(this::linkView).toList();
    }

    public FriendLinkVO createLink(FriendLinkRequest body) {
        guard.require("forum:site");
        long id = IdWorker.getId();
        FriendLink values = new FriendLink();
        values.setId(id);
        values.setName(requiredText(body.getName(), "友链名称", 80));
        values.setUrl(url(body.getUrl()));
        values.setSortNo(intValue(body.getSortNo()));
        values.setEnabled(body.isEnabledPresent() ? bool(body.getEnabled()) : true);
        mapper.insertLink(values);
        return linkView(mapper.findLink(id));
    }

    public FriendLinkVO updateLink(long id, FriendLinkRequest body) {
        guard.require("forum:site");
        FriendLink current = requireLink(id);
        FriendLink values = new FriendLink();
        values.setId(id);
        values.setName(body.isNamePresent() ? requiredText(body.getName(), "友链名称", 80) : current.getName());
        values.setUrl(body.isUrlPresent() ? url(body.getUrl()) : current.getUrl());
        values.setSortNo(body.isSortNoPresent() ? intValue(body.getSortNo()) : intValue(current.getSortNo()));
        values.setEnabled(body.isEnabledPresent() ? bool(body.getEnabled()) : Boolean.TRUE.equals(current.getEnabled()));
        mapper.updateLink(values);
        return linkView(requireLink(id));
    }

    private SiteConfig config() {
        SiteConfig config = mapper.findConfig();
        if (config == null) throw new BizException(ErrorCode.NOT_FOUND, "站点配置不存在");
        return config;
    }

    private FriendLink requireLink(long id) {
        FriendLink link = mapper.findLink(id);
        if (link == null) throw new BizException(ErrorCode.NOT_FOUND, "友情链接不存在");
        return link;
    }

    private FriendLinkVO linkView(FriendLink row) {
        FriendLinkVO view = new FriendLinkVO();
        view.setId(row.getId());
        view.setName(row.getName());
        view.setUrl(row.getUrl());
        view.setSortNo(row.getSortNo());
        view.setEnabled(Boolean.TRUE.equals(row.getEnabled()));
        view.setCreatedAt(row.getCreatedAt());
        view.setUpdatedAt(row.getUpdatedAt());
        return view;
    }

    private String value(Object raw, String key, int maxLength) {
        String value = text(raw).trim();
        if (value.length() > maxLength) throw param(key + " 长度不能超过 " + maxLength + " 个字符");
        return value;
    }

    private String requiredText(Object raw, String name, int maxLength) {
        String value = text(raw).trim();
        if (value.isBlank() || value.length() > maxLength) throw param(name + "长度应为 1-" + maxLength + " 个字符");
        return value;
    }

    private String url(Object raw) {
        String value = text(raw).trim();
        if (value.isBlank() || value.length() > 500) throw param("友链地址长度应为 1-500 个字符");
        try {
            URI uri = URI.create(value);
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null) {
                throw param("友链地址仅支持有效的 http/https 地址");
            }
        } catch (IllegalArgumentException e) {
            throw param("友链地址仅支持有效的 http/https 地址");
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
