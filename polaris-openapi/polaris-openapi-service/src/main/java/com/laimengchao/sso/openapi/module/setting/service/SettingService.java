package com.laimengchao.sso.openapi.module.setting.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laimengchao.sso.openapi.module.log.service.MaskUtils;
import com.laimengchao.sso.openapi.module.setting.entity.ApiOpenSetting;
import com.laimengchao.sso.openapi.module.setting.mapper.ApiOpenSettingMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 平台设置（KV）服务：读取带短时缓存（默认 10s），避免每个请求都查库；保存时主动失效缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingService {

    public static final String KEY_IP_WHITELIST = "ip.whitelist";
    public static final String KEY_GLOBAL_RATE_LIMIT = "global.rate.limit";
    public static final String KEY_NONCE_TTL = "nonce.ttl.seconds";
    public static final String KEY_MASK_RULES = "mask.rules";

    private static final long CACHE_TTL_MS = 10_000;

    private final ApiOpenSettingMapper mapper;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /** 启动时把已持久化的脱敏规则应用到 MaskUtils。 */
    @PostConstruct
    public void init() {
        try {
            ApiOpenSetting row = mapper.selectByKey(KEY_MASK_RULES);
            if (row != null && row.getSettingValue() != null && !row.getSettingValue().isBlank()) {
                MaskUtils.configure(row.getSettingValue());
            }
        } catch (Exception e) {
            log.warn("加载脱敏规则失败，使用默认规则：{}", e.getMessage());
        }
    }

    /** 读取配置值；不存在返回空串。 */
    public String get(String key, String defaultValue) {
        CacheEntry ce = cache.get(key);
        if (ce != null && System.currentTimeMillis() - ce.at < CACHE_TTL_MS) {
            return ce.value;
        }
        ApiOpenSetting row = mapper.selectByKey(key);
        String value = row == null || row.getSettingValue() == null ? "" : row.getSettingValue();
        cache.put(key, new CacheEntry(value, System.currentTimeMillis()));
        return value.isBlank() ? defaultValue : value;
    }

    /** 保存或更新配置（按 setting_key 幂等）。 */
    public void save(String key, String value, String remark) {
        ApiOpenSetting row = mapper.selectByKey(key);
        if (row == null) {
            row = new ApiOpenSetting();
            row.setSettingKey(key);
            row.setSettingValue(value == null ? "" : value);
            row.setRemark(remark);
            row.setCreatedAt(LocalDateTime.now());
            row.setUpdatedAt(LocalDateTime.now());
            mapper.insert(row);
        } else {
            row.setSettingValue(value == null ? "" : value);
            if (remark != null) {
                row.setRemark(remark);
            }
            row.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(row);
        }
        cache.remove(key);
        // 脱敏规则保存后立即生效
        if (KEY_MASK_RULES.equals(key)) {
            MaskUtils.configure(value);
        }
        log.info("平台设置已更新：key={}", key);
    }

    /** 列出全部设置。 */
    public List<ApiOpenSetting> list() {
        return mapper.selectList(Wrappers.<ApiOpenSetting>lambdaQuery()
                .orderByAsc(ApiOpenSetting::getSettingKey));
    }

    private record CacheEntry(String value, long at) {
    }
}
