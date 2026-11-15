package com.laimengchao.sso.cms.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.cms.common.CmsErrorCode;
import com.laimengchao.sso.cms.security.DataScopeService;
import com.laimengchao.sso.cms.module.content.dto.TagVO;
import com.laimengchao.sso.cms.module.content.entity.Tag;
import com.laimengchao.sso.cms.module.content.mapper.ArticleMapper;
import com.laimengchao.sso.cms.module.content.mapper.TagMapper;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** 标签：唯一名 + 占用保护；保存文章时按 id 关联 */
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagMapper tagMapper;
    private final ArticleMapper articleMapper;
    private final DataScopeService dataScopeService;

    public List<TagVO> listAll(String keyword) {
        LambdaQueryWrapper<Tag> w = new LambdaQueryWrapper<>();
        dataScopeService.applyOrgScope(w, Tag::getOrgCode);
        if (StringUtils.hasText(keyword)) {
            w.like(Tag::getName, keyword);
        }
        w.orderByAsc(Tag::getName);
        return tagMapper.selectList(w).stream()
                .map(t -> {
                    TagVO vo = TagVO.of(t.getId(), t.getName(), 0);
                    vo.setArticleCount(articleMapper.countByTag(t.getId()));
                    return vo;
                }).toList();
    }

    /** 创建（按名+orgCode 去重，存在即返回已有） */
    public TagVO create(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 64) {
            throw new BizException(ErrorCode.PARAM_ERROR, "标签名需 1-64 字");
        }
        String orgCode = AccountContext.getDataScopeOrgCode();
        Tag exist = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getName, n)
                .eq(Tag::getOrgCode, orgCode));
        if (exist != null) {
            return TagVO.of(exist.getId(), exist.getName(), articleMapper.countByTag(exist.getId()));
        }
        Tag t = new Tag();
        t.setName(n);
        t.setOrgCode(orgCode);
        tagMapper.insert(t);
        return TagVO.of(t.getId(), t.getName(), 0L);
    }

    public void rename(Long id, String name) {
        Tag t = tagMapper.selectById(id);
        if (t == null) {
            throw new BizException(CmsErrorCode.DATA_NOT_FOUND, "标签不存在");
        }
        dataScopeService.assertCanAccessOrg(t.getOrgCode());
        String n = name == null ? "" : name.trim();
        Tag dup = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getName, n)
                .eq(Tag::getOrgCode, t.getOrgCode()));
        if (dup != null && !dup.getId().equals(id)) {
            throw new BizException(CmsErrorCode.DATA_ALREADY_EXISTS, "标签名已存在");
        }
        t.setName(n);
        tagMapper.updateById(t);
    }

    public void delete(Long id) {
        Tag t = tagMapper.selectById(id);
        if (t == null) {
            throw new BizException(CmsErrorCode.DATA_NOT_FOUND, "标签不存在");
        }
        dataScopeService.assertCanAccessOrg(t.getOrgCode());
        if (articleMapper.countByTag(id) > 0) {
            throw new BizException(CmsErrorCode.TAG_IN_USE);
        }
        tagMapper.deleteById(id);
    }
}
