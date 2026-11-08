package com.laimengchao.sso.settings.module.dict.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.settings.common.SettingsErrorCode;
import com.laimengchao.sso.settings.module.dict.entity.DictItem;
import com.laimengchao.sso.settings.module.dict.entity.DictType;
import com.laimengchao.sso.settings.module.dict.mapper.DictItemMapper;
import com.laimengchao.sso.settings.module.dict.mapper.DictTypeMapper;
import com.laimengchao.sso.settings.module.dict.vo.DictItemVO;
import com.laimengchao.sso.settings.module.dict.vo.DictTypeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 字典服务：管理端 CRUD + 内部读取（带 60s 本地缓存）。
 * <p>
 * 缓存的意义：ehr 这类调用方每个请求都可能要「类型 → 名称」映射，
 * 不能每次都跨服务打一枪；写操作立即失效全部缓存，保证管理端改完最多 0 秒生效（本机），
 * 调用方最长 60 秒后过期重拉。内部接口在类型停用/删除后按「不存在」处理。
 */
@Slf4j
@Service
public class DictService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9._-]{1,63}$");
    private static final long CACHE_TTL_MS = 60_000;

    private final DictTypeMapper typeMapper;
    private final DictItemMapper itemMapper;

    /** 内部读取缓存：typeCode → (items, expireAt) */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public DictService(DictTypeMapper typeMapper, DictItemMapper itemMapper) {
        this.typeMapper = typeMapper;
        this.itemMapper = itemMapper;
    }

    /* ---------------- 管理端：字典类型 ---------------- */

    public List<DictTypeVO> listTypes(String keyword) {
        LambdaQueryWrapper<DictType> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(DictType::getCode, keyword.trim())
                    .or().like(DictType::getName, keyword.trim()));
        }
        qw.orderByAsc(DictType::getAppCode).orderByAsc(DictType::getCode);
        return typeMapper.selectList(qw).stream().map(this::toTypeVO).toList();
    }

    @Transactional
    public DictTypeVO createType(String code, String name, String appCode, String remark) {
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new BizException(SettingsErrorCode.DICT_CODE_INVALID);
        }
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "字典名称不能为空");
        }
        if (findByCode(code) != null) {
            throw new BizException(SettingsErrorCode.DICT_TYPE_DUPLICATED);
        }
        DictType t = new DictType();
        t.setCode(code);
        t.setName(name.trim());
        t.setAppCode(StringUtils.hasText(appCode) ? appCode.trim() : null);
        t.setRemark(remark);
        t.setBuiltIn(0);
        t.setStatus(1);
        typeMapper.insert(t);
        invalidate(code);
        return toTypeVO(t);
    }

    @Transactional
    public DictTypeVO updateType(Long id, String name, String appCode, String remark, Integer status) {
        DictType t = requireType(id);
        // 内置类型：允许改名称/备注/启停，编码与归属应用锁死（调用方按编码拉取，改了等于全线崩）
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "字典名称不能为空");
        }
        t.setName(name.trim());
        if (t.getBuiltIn() == null || t.getBuiltIn() != 1) {
            t.setAppCode(StringUtils.hasText(appCode) ? appCode.trim() : null);
        }
        t.setRemark(remark);
        if (status != null && (status == 0 || status == 1)) {
            t.setStatus(status);
        }
        typeMapper.updateById(t);
        invalidate(t.getCode());
        return toTypeVO(t);
    }

    @Transactional
    public void deleteType(Long id) {
        DictType t = requireType(id);
        if (t.getBuiltIn() != null && t.getBuiltIn() == 1) {
            throw new BizException(SettingsErrorCode.DICT_TYPE_BUILT_IN);
        }
        Long items = itemMapper.selectCount(new LambdaQueryWrapper<DictItem>().eq(DictItem::getTypeCode, t.getCode()));
        if (items != null && items > 0) {
            throw new BizException(SettingsErrorCode.DICT_TYPE_IN_USE);
        }
        typeMapper.deleteById(id);
        invalidate(t.getCode());
    }

    /* ---------------- 管理端：字典项 ---------------- */

    public List<DictItemVO> listItems(String typeCode, Boolean includeDisabled) {
        DictType t = requireTypeByCode(typeCode);
        LambdaQueryWrapper<DictItem> qw = new LambdaQueryWrapper<DictItem>().eq(DictItem::getTypeCode, t.getCode());
        if (!Boolean.TRUE.equals(includeDisabled)) {
            qw.eq(DictItem::getStatus, 1);
        }
        qw.orderByAsc(DictItem::getSort).orderByAsc(DictItem::getId);
        return itemMapper.selectList(qw).stream().map(this::toItemVO).toList();
    }

    @Transactional
    public DictItemVO createItem(String typeCode, String value, String label, Integer sort, String remark) {
        DictType t = requireTypeByCode(typeCode);
        if (!StringUtils.hasText(value) || !StringUtils.hasText(label)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "取值与显示名不能为空");
        }
        if (valueExists(t.getCode(), value, null)) {
            throw new BizException(SettingsErrorCode.DICT_ITEM_DUPLICATED);
        }
        DictItem i = new DictItem();
        i.setTypeCode(t.getCode());
        i.setValue(value.trim());
        i.setLabel(label.trim());
        i.setSort(sort == null ? 0 : sort);
        i.setRemark(remark);
        i.setStatus(1);
        i.setBuiltIn(0);
        itemMapper.insert(i);
        invalidate(t.getCode());
        return toItemVO(i);
    }

    @Transactional
    public DictItemVO updateItem(Long id, String label, Integer sort, Integer status, String remark) {
        DictItem i = itemMapper.selectById(id);
        if (i == null) {
            throw new BizException(SettingsErrorCode.DICT_ITEM_NOT_FOUND);
        }
        if (!StringUtils.hasText(label)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "显示名不能为空");
        }
        i.setLabel(label.trim());
        if (sort != null) {
            i.setSort(sort);
        }
        if (status != null && (status == 0 || status == 1)) {
            i.setStatus(status);
        }
        i.setRemark(remark);
        itemMapper.updateById(i);
        invalidate(i.getTypeCode());
        return toItemVO(i);
    }

    @Transactional
    public void deleteItem(Long id) {
        DictItem i = itemMapper.selectById(id);
        if (i == null) {
            throw new BizException(SettingsErrorCode.DICT_ITEM_NOT_FOUND);
        }
        if (i.getBuiltIn() != null && i.getBuiltIn() == 1) {
            throw new BizException(SettingsErrorCode.DICT_ITEM_BUILT_IN);
        }
        itemMapper.deleteById(id);
        invalidate(i.getTypeCode());
    }

    /* ---------------- 内部接口（跨服务拉取） ---------------- */

    /**
     * 类型编码 → 启用项列表（value/label/sort）。类型不存在或已停用返回空列表，
     * 不抛异常——调用方拿到空列表自然走自己的兜底。
     */
    public List<DictItemVO> internalItems(String typeCode) {
        long now = Instant.now().toEpochMilli();
        CacheEntry entry = cache.get(typeCode);
        if (entry != null && now < entry.expireAt) {
            return entry.items;
        }
        List<DictItemVO> items = loadEnabledItems(typeCode);
        cache.put(typeCode, new CacheEntry(items, now + CACHE_TTL_MS));
        return items;
    }

    /** 批量：typeCodes → { code: items }，减少调用方启动时的往返次数 */
    public Map<String, List<DictItemVO>> internalBatch(List<String> typeCodes) {
        if (typeCodes == null) {
            return Map.of();
        }
        return typeCodes.stream().distinct()
                .collect(Collectors.toMap(c -> c, this::internalItems, (a, b) -> a,
                        java.util.LinkedHashMap::new));
    }

    private List<DictItemVO> loadEnabledItems(String typeCode) {
        DictType t = findByCode(typeCode);
        if (t == null || t.getStatus() == null || t.getStatus() != 1) {
            return List.of();
        }
        return itemMapper.selectList(new LambdaQueryWrapper<DictItem>()
                        .eq(DictItem::getTypeCode, typeCode)
                        .eq(DictItem::getStatus, 1))
                .stream()
                .sorted(Comparator.comparing(DictItem::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(DictItem::getId))
                .map(this::toItemVO)
                .toList();
    }

    private void invalidate(String typeCode) {
        if (typeCode != null) {
            cache.remove(typeCode);
        }
    }

    /* ---------------- 私有 ---------------- */

    private record CacheEntry(List<DictItemVO> items, long expireAt) {
    }

    private DictType findByCode(String code) {
        return typeMapper.selectOne(new LambdaQueryWrapper<DictType>().eq(DictType::getCode, code));
    }

    private DictType requireType(Long id) {
        DictType t = typeMapper.selectById(id);
        if (t == null) {
            throw new BizException(SettingsErrorCode.DICT_TYPE_NOT_FOUND);
        }
        return t;
    }

    private DictType requireTypeByCode(String typeCode) {
        if (!StringUtils.hasText(typeCode)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "typeCode 不能为空");
        }
        DictType t = findByCode(typeCode.trim());
        if (t == null) {
            throw new BizException(SettingsErrorCode.DICT_TYPE_NOT_FOUND);
        }
        return t;
    }

    private boolean valueExists(String typeCode, String value, Long excludeId) {
        LambdaQueryWrapper<DictItem> qw = new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getTypeCode, typeCode)
                .eq(DictItem::getValue, value);
        if (excludeId != null) {
            qw.ne(DictItem::getId, excludeId);
        }
        return itemMapper.selectCount(qw) > 0;
    }

    private DictTypeVO toTypeVO(DictType t) {
        DictTypeVO vo = new DictTypeVO();
        vo.setId(t.getId());
        vo.setCode(t.getCode());
        vo.setName(t.getName());
        vo.setAppCode(t.getAppCode());
        vo.setRemark(t.getRemark());
        vo.setBuiltIn(t.getBuiltIn());
        vo.setStatus(t.getStatus());
        vo.setItemCount(itemMapper.selectCount(
                new LambdaQueryWrapper<DictItem>().eq(DictItem::getTypeCode, t.getCode())));
        vo.setCreatedAt(t.getCreatedAt());
        vo.setUpdatedAt(t.getUpdatedAt());
        return vo;
    }

    private DictItemVO toItemVO(DictItem i) {
        DictItemVO vo = new DictItemVO();
        vo.setId(i.getId());
        vo.setTypeCode(i.getTypeCode());
        vo.setValue(i.getValue());
        vo.setLabel(i.getLabel());
        vo.setSort(i.getSort());
        vo.setStatus(i.getStatus());
        vo.setRemark(i.getRemark());
        vo.setBuiltIn(i.getBuiltIn());
        vo.setCreatedAt(i.getCreatedAt());
        vo.setUpdatedAt(i.getUpdatedAt());
        return vo;
    }
}
