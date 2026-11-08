package com.laimengchao.sso.cms.module.portal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.cms.common.CmsErrorCode;
import com.laimengchao.sso.cms.module.portal.entity.PortalLayout;
import com.laimengchao.sso.cms.module.portal.mapper.PortalLayoutMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 门户首页布局配置：管理端增改排序，门户端只读已启用模块 */
@Service
@RequiredArgsConstructor
public class PortalLayoutService {

    private final PortalLayoutMapper mapper;

    /** 默认模块（首次启动种子）。config 用 JSON 串描述每个区块的取数参数 */
    private static final List<PortalLayout> DEFAULTS = List.of(
            def("banner", "Banner 轮播", 1, 1, "{\"limit\":5}"),
            def("featured", "精选推荐", 1, 2, "{\"limit\":3}"),
            def("latest", "最新文章", 1, 3, "{\"limit\":6}"),
            def("category", "分类导航", 1, 4, "{}"),
            def("tags", "热门标签", 0, 5, "{\"limit\":12}"),
            def("links", "友情链接", 0, 6, "{}")
    );

    private static PortalLayout def(String key, String name, int enabled, int sort, String config) {
        PortalLayout l = new PortalLayout();
        l.setModuleKey(key);
        l.setModuleName(name);
        l.setEnabled(enabled);
        l.setSort(sort);
        l.setConfig(config);
        return l;
    }

    /** 首次启动（表为空）时写入默认模块，避免门户空白 */
    @PostConstruct
    public void initDefaults() {
        Long count = mapper.selectCount(new LambdaQueryWrapper<>());
        if (count != null && count > 0) {
            return;
        }
        for (PortalLayout d : DEFAULTS) {
            mapper.insert(d);
        }
    }

    /** 管理端：全部模块（含隐藏），按 sort 排 */
    public List<PortalLayout> listAll() {
        LambdaQueryWrapper<PortalLayout> w = new LambdaQueryWrapper<>();
        w.orderByAsc(PortalLayout::getSort).orderByAsc(PortalLayout::getId);
        return mapper.selectList(w);
    }

    /** 门户端：仅启用模块，按 sort 排 */
    public List<PortalLayout> listEnabled() {
        LambdaQueryWrapper<PortalLayout> w = new LambdaQueryWrapper<>();
        w.eq(PortalLayout::getEnabled, 1);
        w.orderByAsc(PortalLayout::getSort).orderByAsc(PortalLayout::getId);
        return mapper.selectList(w);
    }

    /**
     * 保存布局：按 moduleKey 幂等 upsert，sort 以传入顺序重排（1..n）。
     * 不删除未出现的模块（模块集合是固定的），避免误删配置。
     */
    @Transactional
    public List<PortalLayout> saveLayout(List<PortalLayout> items) {
        if (items == null || items.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "布局不能为空");
        }
        Map<String, PortalLayout> existing = listAll().stream()
                .collect(Collectors.toMap(PortalLayout::getModuleKey, Function.identity()));
        Set<String> seen = new LinkedHashSet<>();
        int sort = 1;
        for (PortalLayout it : items) {
            if (it.getModuleKey() == null || it.getModuleKey().isBlank()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "模块标识不能为空");
            }
            String key = it.getModuleKey().trim();
            if (!seen.add(key)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "模块重复：" + key);
            }
            it.setModuleKey(key);
            it.setSort(sort++);
            it.setEnabled(it.getEnabled() == null ? 1 : it.getEnabled());
            it.setConfig(it.getConfig() == null ? "" : it.getConfig());
            PortalLayout db = existing.get(key);
            if (db == null) {
                if (it.getModuleName() == null || it.getModuleName().isBlank()) {
                    it.setModuleName(key);
                }
                it.setId(null);
                mapper.insert(it);
            } else {
                it.setId(db.getId());
                // 模块名以库中为准，不允许前端改名（避免 UI 文案被篡改）
                it.setModuleName(db.getModuleName());
                mapper.updateById(it);
            }
        }
        return listAll();
    }
}
