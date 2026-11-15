package com.laimengchao.sso.cms.module.banner.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.cms.common.CmsErrorCode;
import com.laimengchao.sso.cms.security.DataScopeService;
import com.laimengchao.sso.cms.module.banner.entity.Banner;
import com.laimengchao.sso.cms.module.banner.mapper.BannerMapper;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** Banner 管理 */
@Service
@RequiredArgsConstructor
public class BannerService {

    private final BannerMapper bannerMapper;
    private final DataScopeService dataScopeService;

    public List<Banner> listAll() {
        LambdaQueryWrapper<Banner> w = new LambdaQueryWrapper<>();
        dataScopeService.applyOrgScope(w, Banner::getOrgCode);
        w.orderByAsc(Banner::getSort);
        w.orderByAsc(Banner::getId);
        return bannerMapper.selectList(w);
    }

    public Banner save(Banner banner) {
        if (banner.getTitle() == null || banner.getTitle().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "标题不能为空");
        }
        if (banner.getImageUrl() == null || banner.getImageUrl().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片不能为空");
        }
        banner.setTitle(banner.getTitle().trim());
        banner.setImageUrl(banner.getImageUrl().trim());
        banner.setLinkUrl(StringUtils.hasText(banner.getLinkUrl()) ? banner.getLinkUrl().trim() : "");
        banner.setSort(banner.getSort() == null ? 0 : banner.getSort());
        banner.setStatus(banner.getStatus() == null ? 1 : banner.getStatus());
        if (banner.getId() == null) {
            banner.setOrgCode(AccountContext.getDataScopeOrgCode());
            bannerMapper.insert(banner);
        } else {
            Banner existing = bannerMapper.selectById(banner.getId());
            if (existing == null) {
                throw new BizException(CmsErrorCode.DATA_NOT_FOUND, "Banner 不存在");
            }
            dataScopeService.assertCanAccessOrg(existing.getOrgCode());
            bannerMapper.updateById(banner);
        }
        return banner;
    }

    public void delete(Long id) {
        Banner existing = bannerMapper.selectById(id);
        if (existing == null) {
            throw new BizException(CmsErrorCode.DATA_NOT_FOUND, "Banner 不存在");
        }
        dataScopeService.assertCanAccessOrg(existing.getOrgCode());
        bannerMapper.deleteById(id);
    }
}
