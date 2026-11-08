package com.laimengchao.sso.cms.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.cms.common.CmsErrorCode;
import com.laimengchao.sso.cms.module.content.dto.CategorySaveRequest;
import com.laimengchao.sso.cms.module.content.dto.TagVO;
import com.laimengchao.sso.cms.module.content.entity.Category;
import com.laimengchao.sso.cms.module.content.mapper.ArticleMapper;
import com.laimengchao.sso.cms.module.content.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 分类：软删除 + 占用保护 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;

    public List<TagVO> listAll() {
        LambdaQueryWrapper<Category> w = new LambdaQueryWrapper<>();
        w.orderByAsc(Category::getSort);
        w.orderByAsc(Category::getId);
        return categoryMapper.selectList(w).stream()
                .map(c -> {
                    TagVO vo = TagVO.of(c.getId(), c.getName(), 0);
                    vo.setSort(c.getSort());
                    vo.setArticleCount(articleMapper.countByCategory(c.getId()));
                    return vo;
                }).toList();
    }

    public void save(CategorySaveRequest req) {
        String name = req.getName().trim();
        Category dup = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                .eq(Category::getName, name));
        if (dup != null && !dup.getId().equals(req.getId())) {
            throw new BizException(CmsErrorCode.DATA_ALREADY_EXISTS, "分类名已存在");
        }
        Category c;
        if (req.getId() == null) {
            c = new Category();
        } else {
            c = categoryMapper.selectById(req.getId());
            if (c == null) {
                throw new BizException(CmsErrorCode.DATA_NOT_FOUND, "分类不存在");
            }
        }
        c.setName(name);
        c.setSort(req.getSort() == null ? 0 : req.getSort());
        if (req.getId() == null) {
            categoryMapper.insert(c);
        } else {
            categoryMapper.updateById(c);
        }
    }

    public void delete(Long id) {
        if (articleMapper.countByCategory(id) > 0) {
            throw new BizException(CmsErrorCode.CATEGORY_IN_USE);
        }
        categoryMapper.deleteById(id);
    }
}
