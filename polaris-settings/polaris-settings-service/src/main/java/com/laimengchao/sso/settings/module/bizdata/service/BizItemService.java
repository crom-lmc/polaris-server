package com.laimengchao.sso.settings.module.bizdata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.settings.common.SettingsErrorCode;
import com.laimengchao.sso.settings.module.bizdata.entity.BizBank;
import com.laimengchao.sso.settings.module.bizdata.entity.BizEducation;
import com.laimengchao.sso.settings.module.bizdata.entity.BizFamilyRelation;
import com.laimengchao.sso.settings.module.bizdata.entity.BizLeaveType;
import com.laimengchao.sso.settings.module.bizdata.entity.BizNamed;
import com.laimengchao.sso.settings.module.bizdata.entity.BizPosition;
import com.laimengchao.sso.settings.module.bizdata.entity.BizResignReason;
import com.laimengchao.sso.settings.module.bizdata.mapper.BizBankMapper;
import com.laimengchao.sso.settings.module.bizdata.mapper.BizEducationMapper;
import com.laimengchao.sso.settings.module.bizdata.mapper.BizFamilyRelationMapper;
import com.laimengchao.sso.settings.module.bizdata.mapper.BizLeaveTypeMapper;
import com.laimengchao.sso.settings.module.bizdata.mapper.BizPositionMapper;
import com.laimengchao.sso.settings.module.bizdata.mapper.BizResignReasonMapper;
import com.laimengchao.sso.settings.module.bizdata.vo.BizCategoryVO;
import com.laimengchao.sso.settings.module.bizdata.vo.BizItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 主数据服务：六类业务主数据（银行/学历/职位/请假类型/离职原因/家庭成员关系）的
 * 管理端 CRUD + 内部读取（60s 缓存）。
 * <p>
 * 为什么独立于字典：字典（cfg_dict_*）收口的是「性别/婚姻/民族/证件类型」这类
 * 纯枚举事实；银行、职位、请假类型是业务主数据——会被业务表按 code 引用、
 * 需要独立演进专属字段（银行的联行号、职位的职级），所以各建真表维护。
 * <p>
 * 类别固定六类，不支持新增类别（要加新类别 = 加一张真表 + 在 {@link #categories()} 注册，
 * 这是刻意设计的扩展点，防止管理页把「建表」变成随手点按钮）。
 * <p>
 * 实现注记：六张表字段结构当前一致，所以用 {@link BizNamed} 接口 + 裸 QueryWrapper
 * （字符串列名）统一分发——**不要**换成 LambdaQueryWrapper + BizNamed::getXxx 方法引用，
 * MyBatis-Plus 的 lambda 列缓存解析到的是接口而不是具体实体，会直接抛
 * 「can not find lambda cache」；某类加了专属字段后，特殊逻辑写在对应分支即可。
 */
@Slf4j
@Service
public class BizItemService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,31}$");
    private static final long CACHE_TTL_MS = 60_000;

    /** 一个类别 = 一张真表 + 展示名 */
    public record Category(String type, String name, String remark, BaseMapper<? extends BizNamed> mapper) {
    }

    private final BizBankMapper bankMapper;
    private final BizEducationMapper educationMapper;
    private final BizPositionMapper positionMapper;
    private final BizLeaveTypeMapper leaveTypeMapper;
    private final BizResignReasonMapper resignReasonMapper;
    private final BizFamilyRelationMapper familyRelationMapper;

    /** 内部读取缓存：type → (items, expireAt) */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private volatile Map<String, Category> categoryMap;

    public BizItemService(BizBankMapper bankMapper,
                          BizEducationMapper educationMapper,
                          BizPositionMapper positionMapper,
                          BizLeaveTypeMapper leaveTypeMapper,
                          BizResignReasonMapper resignReasonMapper,
                          BizFamilyRelationMapper familyRelationMapper) {
        this.bankMapper = bankMapper;
        this.educationMapper = educationMapper;
        this.positionMapper = positionMapper;
        this.leaveTypeMapper = leaveTypeMapper;
        this.resignReasonMapper = resignReasonMapper;
        this.familyRelationMapper = familyRelationMapper;
    }

    /** 类别注册表：顺序即管理页 Tab 顺序；与 schema.sql 建表顺序保持一致 */
    private Map<String, Category> categories() {
        Map<String, Category> m = categoryMap;
        if (m == null) {
            synchronized (this) {
                m = categoryMap;
                if (m == null) {
                    m = new LinkedHashMap<>();
                    m.put("bank", new Category("bank", "银行",
                            "员工工资卡/报销卡的发卡行", bankMapper));
                    m.put("education", new Category("education", "学历",
                            "教育背景与最高学历共用", educationMapper));
                    m.put("position", new Category("position", "职位",
                            "岗位序列与职位名称", positionMapper));
                    m.put("leave_type", new Category("leave_type", "请假类型",
                            "请假申请的类型清单", leaveTypeMapper));
                    m.put("resign_reason", new Category("resign_reason", "离职/减员原因",
                            "离职与减员操作的原因分类", resignReasonMapper));
                    m.put("family_relation", new Category("family_relation", "家庭成员关系",
                            "家庭成员与本人的关系", familyRelationMapper));
                    categoryMap = m;
                }
            }
        }
        return m;
    }

    /* ---------------- 管理端 ---------------- */

    public List<BizCategoryVO> listCategories() {
        return categories().values().stream().map(c -> {
            BizCategoryVO vo = new BizCategoryVO();
            vo.setType(c.type());
            vo.setName(c.name());
            vo.setRemark(c.remark());
            vo.setItemCount(mapperOf(c.type()).selectCount(qwOf(c.type(), null)));
            return vo;
        }).toList();
    }

    public List<BizItemVO> listItems(String type, Boolean includeDisabled) {
        requireCategory(type);
        QueryWrapper<BizNamed> qw = qwOf(type, Boolean.TRUE.equals(includeDisabled) ? null : 1);
        return mapperOf(type).selectList(qw).stream()
                .sorted(Comparator.comparing(BizNamed::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(BizNamed::getId))
                .map(e -> toVO(type, e))
                .toList();
    }

    @Transactional
    public BizItemVO createItem(String type, String code, String name, Integer sort, String remark, String category) {
        requireCategory(type);
        if (!StringUtils.hasText(code) || !CODE_PATTERN.matcher(code.trim()).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "取值编码不合法（字母开头，仅字母/数字/下划线/中划线，≤32 位）");
        }
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "展示名不能为空");
        }
        String trimmed = code.trim();
        if (codeExists(type, trimmed, null)) {
            throw new BizException(SettingsErrorCode.BIZ_ITEM_DUPLICATED);
        }
        BizNamed e = newEntity(type);
        e.setCode(trimmed);
        e.setName(name.trim());
        e.setSort(sort == null ? 0 : sort);
        e.setStatus(1);
        e.setRemark(remark);
        e.setBuiltIn(0);
        if (e instanceof BizPosition bp && category != null) {
            bp.setCategory(category);
        }
        mapperOf(type).insert(e);
        invalidate(type);
        return toVO(type, e);
    }

    @Transactional
    public BizItemVO updateItem(String type, Long id, String name, Integer sort, Integer status, String remark, String category) {
        requireCategory(type);
        BizNamed e = requireItem(type, id);
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "展示名不能为空");
        }
        e.setName(name.trim());
        if (sort != null) {
            e.setSort(sort);
        }
        if (status != null && (status == 0 || status == 1)) {
            e.setStatus(status);
        }
        e.setRemark(remark);
        if (e instanceof BizPosition bp && category != null) {
            bp.setCategory(category);
        }
        mapperOf(type).updateById(e);
        invalidate(type);
        return toVO(type, e);
    }

    @Transactional
    public void deleteItem(String type, Long id) {
        requireCategory(type);
        BizNamed e = requireItem(type, id);
        if (e.getBuiltIn() != null && e.getBuiltIn() == 1) {
            throw new BizException(SettingsErrorCode.BIZ_ITEM_BUILT_IN);
        }
        mapperOf(type).deleteById(id);
        invalidate(type);
    }

    /* ---------------- 内部接口（跨服务拉取） ---------------- */

    /**
     * 类别编码 → 启用项列表（保持 sort 排序）。类别不存在/故障返回空列表不抛异常，
     * 调用方拿到空列表走自己的内置兜底（与字典内部接口同一语义）。
     */
    public List<BizItemVO> internalItems(String type) {
        if (!categories().containsKey(type)) {
            return List.of();
        }
        long now = Instant.now().toEpochMilli();
        CacheEntry entry = cache.get(type);
        if (entry != null && now < entry.expireAt) {
            return entry.items;
        }
        List<BizItemVO> items = mapperOf(type)
                .selectList(qwOf(type, 1))
                .stream()
                .sorted(Comparator.comparing(BizNamed::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(BizNamed::getId))
                .map(e -> toVO(type, e))
                .toList();
        cache.put(type, new CacheEntry(items, now + CACHE_TTL_MS));
        return items;
    }

    private void invalidate(String type) {
        if (type != null) {
            cache.remove(type);
        }
    }

    /* ---------------- 私有 ---------------- */

    private record CacheEntry(List<BizItemVO> items, long expireAt) {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private BaseMapper<BizNamed> mapperOf(String type) {
        return (BaseMapper) requireCategory(type).mapper();
    }

    /** 字符串列名查询条件：跨六张表统一；status 传 null 表示不过滤 */
    private QueryWrapper<BizNamed> qwOf(String type, Integer status) {
        requireCategory(type);
        QueryWrapper<BizNamed> qw = new QueryWrapper<>();
        if (status != null) {
            qw.eq("status", status);
        }
        qw.orderByAsc("sort").orderByAsc("id");
        return qw;
    }

    private BizNamed newEntity(String type) {
        return switch (type) {
            case "bank" -> new BizBank();
            case "education" -> new BizEducation();
            case "position" -> new BizPosition();
            case "leave_type" -> new BizLeaveType();
            case "resign_reason" -> new BizResignReason();
            case "family_relation" -> new BizFamilyRelation();
            default -> throw new BizException(SettingsErrorCode.BIZ_TYPE_UNKNOWN);
        };
    }

    private Category requireCategory(String type) {
        if (!StringUtils.hasText(type)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "type 不能为空");
        }
        Category c = categories().get(type.trim());
        if (c == null) {
            throw new BizException(SettingsErrorCode.BIZ_TYPE_UNKNOWN);
        }
        return c;
    }

    private BizNamed requireItem(String type, Long id) {
        BizNamed e = mapperOf(type).selectById(id);
        if (e == null) {
            throw new BizException(SettingsErrorCode.BIZ_ITEM_NOT_FOUND);
        }
        return e;
    }

    private boolean codeExists(String type, String code, Long excludeId) {
        QueryWrapper<BizNamed> qw = new QueryWrapper<BizNamed>().eq("code", code);
        if (excludeId != null) {
            qw.ne("id", excludeId);
        }
        return mapperOf(type).selectCount(qw) > 0;
    }

    private BizItemVO toVO(String type, BizNamed e) {
        BizItemVO vo = new BizItemVO();
        vo.setId(e.getId());
        vo.setType(type);
        vo.setCode(e.getCode());
        vo.setName(e.getName());
        vo.setSort(e.getSort());
        vo.setStatus(e.getStatus());
        vo.setRemark(e.getRemark());
        vo.setBuiltIn(e.getBuiltIn());
        if (e instanceof BizPosition bp) {
            vo.setCategory(bp.getCategory());
            vo.setJobFamily(bp.getJobFamily());
            vo.setJobFamilyName(bp.getJobFamilyName());
            vo.setSeries(bp.getSeries());
            vo.setLevelNo(bp.getLevelNo());
            vo.setLevelName(bp.getLevelName());
        }
        return vo;
    }
}
