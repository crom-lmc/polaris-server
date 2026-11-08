package com.laimengchao.sso.settings.client.support;

import com.laimengchao.sso.settings.client.api.BankApi;
import com.laimengchao.sso.settings.client.api.DictApi;
import com.laimengchao.sso.settings.client.api.EducationApi;
import com.laimengchao.sso.settings.client.api.FamilyRelationApi;
import com.laimengchao.sso.settings.client.api.LeaveTypeApi;
import com.laimengchao.sso.settings.client.api.PositionApi;
import com.laimengchao.sso.settings.client.api.ResignReasonApi;
import com.laimengchao.sso.settings.client.dto.DictBatchRequest;
import com.laimengchao.sso.settings.client.dto.DictItemDTO;
import com.laimengchao.sso.settings.client.dto.R;
import com.laimengchao.sso.settings.client.dto.BizItemDTO;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 配置中心跨服客户端（对外门面）：在分类型的 Feign 查询接口
 * （{@link DictApi} + 六个主数据 Api）之上加 60s 本地缓存与故障兜底。
 * <p>
 * 字典/主数据是读多写极少的数据，不能每个请求都跨服务打一枪；配置中心改完最多 60 秒生效。
 * 刻意的宽松语义：配置中心挂了 / 类型不存在时返回空集合而不是抛异常，
 * 调用方拿空集合走内置兜底——配置中心的故障不放大成业务服务的故障。
 * <p>
 * Bean 由 {@code com.laimengchao.sso.settings.client.SettingsClientAutoConfiguration}
 * 自动装配（引入本模块即用），也可自行声明同类型 Bean 覆盖（@ConditionalOnMissingBean 让位）。
 */
@Slf4j
public class SettingsClient {

    private static final long CACHE_TTL_MS = 60_000;

    private final DictApi dictApi;
    private final BankApi bankApi;
    private final EducationApi educationApi;
    private final PositionApi positionApi;
    private final LeaveTypeApi leaveTypeApi;
    private final ResignReasonApi resignReasonApi;
    private final FamilyRelationApi familyRelationApi;

    /** typeCode → (items, 过期时间)；dict() 的 value→label 从 items 派生，保证两个方法同源 */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /** 主数据缓存：biz:{type} → (items, expireAt)，与字典缓存分开避免 key 撞车 */
    private final Map<String, BizCacheEntry> bizCache = new ConcurrentHashMap<>();

    public SettingsClient(DictApi dictApi, BankApi bankApi, EducationApi educationApi,
                          PositionApi positionApi, LeaveTypeApi leaveTypeApi,
                          ResignReasonApi resignReasonApi, FamilyRelationApi familyRelationApi) {
        this.dictApi = dictApi;
        this.bankApi = bankApi;
        this.educationApi = educationApi;
        this.positionApi = positionApi;
        this.leaveTypeApi = leaveTypeApi;
        this.resignReasonApi = resignReasonApi;
        this.familyRelationApi = familyRelationApi;
    }

    /**
     * 拉字典：value → label（LinkedHashMap，保持配置中心里的排序）。
     * 服务不可用 / 类型为空，一律返回空 Map。
     */
    public Map<String, String> dict(String typeCode) {
        Map<String, String> out = new LinkedHashMap<>();
        for (DictItemDTO item : items(typeCode)) {
            out.put(item.getValue(), item.getLabel());
        }
        return out;
    }

    /** 拉字典完整项（含 sort/remark 等），60s 缓存、故障返回空列表 */
    public List<DictItemDTO> items(String typeCode) {
        long now = Instant.now().toEpochMilli();
        CacheEntry entry = cache.get(typeCode);
        if (entry != null && now < entry.expireAt) {
            return entry.items;
        }
        List<DictItemDTO> result = fetchDictItems(typeCode);
        if (!result.isEmpty()) {
            cache.put(typeCode, new CacheEntry(result, now + CACHE_TTL_MS));
        }
        return result;
    }

    /**
     * 字典批量拉取：命中缓存的类型直接用，未命中的一次 batch 请求拉回后逐类型入缓存。
     * 返回 LinkedHashMap（按入参顺序），某个类型失败只影响它自己（空列表）。
     */
    public Map<String, List<DictItemDTO>> dictBatch(Collection<String> typeCodes) {
        Map<String, List<DictItemDTO>> out = new LinkedHashMap<>();
        if (typeCodes == null || typeCodes.isEmpty()) {
            return out;
        }
        long now = Instant.now().toEpochMilli();
        List<String> missing = new java.util.ArrayList<>();
        for (String typeCode : typeCodes) {
            CacheEntry entry = cache.get(typeCode);
            if (entry != null && now < entry.expireAt) {
                out.put(typeCode, entry.items);
            } else {
                missing.add(typeCode);
            }
        }
        if (!missing.isEmpty()) {
            Map<String, List<DictItemDTO>> fetched = fetchDictBatch(missing);
            for (String typeCode : missing) {
                List<DictItemDTO> items = fetched.getOrDefault(typeCode, List.of());
                if (!items.isEmpty()) {
                    cache.put(typeCode, new CacheEntry(items, now + CACHE_TTL_MS));
                }
                out.put(typeCode, items);
            }
        }
        return out;
    }

    /** 失效缓存：一般用不到（60s 自然过期），测试与运维排查时方便 */
    public void evict(String typeCode) {
        cache.remove(typeCode);
    }

    /**
     * 拉业务主数据（bank/education/position/leave_type/resign_reason/family_relation），
     * 60s 缓存、故障兜底语义与字典一致。
     *
     * @param type 类别编码，未知类型返回空列表
     */
    public List<BizItemDTO> bizItems(String type) {
        long now = Instant.now().toEpochMilli();
        String key = "biz:" + type;
        BizCacheEntry entry = bizCache.get(key);
        if (entry != null && now < entry.expireAt) {
            return entry.items;
        }
        List<BizItemDTO> result = fetchBizItems(type);
        if (!result.isEmpty()) {
            bizCache.put(key, new BizCacheEntry(result, now + CACHE_TTL_MS));
        }
        return result;
    }

    /** 便捷方法：type → (code→name 映射，LinkedHashMap 保持排序），语义同 {@link #dict(String)} */
    public Map<String, String> bizMap(String type) {
        Map<String, String> out = new LinkedHashMap<>();
        for (BizItemDTO item : bizItems(type)) {
            out.put(item.getCode(), item.getName());
        }
        return out;
    }

    /** 失效主数据缓存 */
    public void evictBiz(String type) {
        bizCache.remove("biz:" + type);
    }

    /**
     * 类型编码 → 对应 Api 的查询调用。每个主数据类型一个 Api 接口，
     * 这里只做「类型 → 接口方法」的路由，不承载业务逻辑。
     */
    private Supplier<R<List<BizItemDTO>>> route(String type) {
        return switch (type) {
            case "bank" -> bankApi::list;
            case "education" -> educationApi::list;
            case "position" -> positionApi::list;
            case "leave_type" -> leaveTypeApi::list;
            case "resign_reason" -> resignReasonApi::list;
            case "family_relation" -> familyRelationApi::list;
            default -> null;
        };
    }

    private List<BizItemDTO> fetchBizItems(String type) {
        Supplier<R<List<BizItemDTO>>> call = route(type);
        if (call == null) {
            log.warn("未知的业务主数据类型 {}，返回空列表", type);
            return List.of();
        }
        try {
            return unwrap(call.get());
        } catch (FeignException e) {
            log.warn("读取配置中心主数据 {} 失败（HTTP {}），本次走内置兜底", type, e.status());
            return List.of();
        } catch (Exception e) {
            log.warn("读取配置中心主数据 {} 失败（{}），本次走内置兜底", type, e.getMessage());
            return List.of();
        }
    }

    private List<DictItemDTO> fetchDictItems(String typeCode) {
        try {
            R<List<DictItemDTO>> resp = dictApi.dictItems(typeCode);
            return unwrap(resp);
        } catch (FeignException e) {
            log.warn("读取配置中心字典 {} 失败（HTTP {}），本次走内置兜底", typeCode, e.status());
            return List.of();
        } catch (Exception e) {
            log.warn("读取配置中心字典 {} 失败（{}），本次走内置兜底", typeCode, e.getMessage());
            return List.of();
        }
    }

    private Map<String, List<DictItemDTO>> fetchDictBatch(List<String> typeCodes) {
        try {
            R<Map<String, List<DictItemDTO>>> resp =
                    dictApi.dictBatch(new DictBatchRequest(typeCodes));
            if (resp != null && resp.ok() && resp.getData() != null) {
                return resp.getData();
            }
            return Map.of();
        } catch (FeignException e) {
            log.warn("批量读取配置中心字典失败（HTTP {}），本次走内置兜底", e.status());
            return Map.of();
        } catch (Exception e) {
            log.warn("批量读取配置中心字典失败（{}），本次走内置兜底", e.getMessage());
            return Map.of();
        }
    }

    private static <T> List<T> unwrap(R<List<T>> resp) {
        return resp != null && resp.ok() && resp.getData() != null ? resp.getData() : List.of();
    }

    private record CacheEntry(List<DictItemDTO> items, long expireAt) {
    }

    private record BizCacheEntry(List<BizItemDTO> items, long expireAt) {
    }
}
