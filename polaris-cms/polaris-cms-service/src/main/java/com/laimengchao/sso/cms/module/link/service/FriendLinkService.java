package com.laimengchao.sso.cms.module.link.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.cms.common.CmsErrorCode;
import com.laimengchao.sso.cms.security.DataScopeService;
import com.laimengchao.sso.cms.module.link.entity.FriendLink;
import com.laimengchao.sso.cms.module.link.mapper.FriendLinkMapper;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** 友情链接管理 */
@Service
@RequiredArgsConstructor
public class FriendLinkService {

    private final FriendLinkMapper friendLinkMapper;
    private final DataScopeService dataScopeService;

    /** 管理端：全部（含停用） */
    public List<FriendLink> listAll() {
        LambdaQueryWrapper<FriendLink> w = new LambdaQueryWrapper<>();
        dataScopeService.applyOrgScope(w, FriendLink::getOrgCode);
        w.orderByAsc(FriendLink::getSort);
        w.orderByAsc(FriendLink::getId);
        return friendLinkMapper.selectList(w);
    }

    /** 门户：仅启用 */
    public List<FriendLink> listEnabled() {
        LambdaQueryWrapper<FriendLink> w = new LambdaQueryWrapper<>();
        w.eq(FriendLink::getStatus, 1);
        w.orderByAsc(FriendLink::getSort);
        w.orderByAsc(FriendLink::getId);
        return friendLinkMapper.selectList(w);
    }

    public FriendLink save(FriendLink link) {
        if (!StringUtils.hasText(link.getName())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "站点名称不能为空");
        }
        link.setName(link.getName().trim());
        link.setUrl(trim(link.getUrl()));
        link.setLogo(trim(link.getLogo()));
        link.setDescription(trim(link.getDescription()));
        link.setSort(link.getSort() == null ? 0 : link.getSort());
        link.setStatus(link.getStatus() == null ? 1 : link.getStatus());
        if (link.getId() == null) {
            link.setOrgCode(AccountContext.getDataScopeOrgCode());
            friendLinkMapper.insert(link);
        } else {
            FriendLink existing = friendLinkMapper.selectById(link.getId());
            if (existing == null) {
                throw new BizException(CmsErrorCode.DATA_NOT_FOUND, "友情链接不存在");
            }
            dataScopeService.assertCanAccessOrg(existing.getOrgCode());
            friendLinkMapper.updateById(link);
        }
        return link;
    }

    public void delete(Long id) {
        FriendLink existing = friendLinkMapper.selectById(id);
        if (existing == null) {
            throw new BizException(CmsErrorCode.DATA_NOT_FOUND, "友情链接不存在");
        }
        dataScopeService.assertCanAccessOrg(existing.getOrgCode());
        friendLinkMapper.deleteById(id);
    }

    private String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : "";
    }
}
