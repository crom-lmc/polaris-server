package com.laimengchao.sso.account.integration;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.ehr.client.api.EhrOrgApi;
import com.laimengchao.sso.ehr.client.dto.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 账号中心 → EHR 的组织维度反查客户端（Feign）。
 * <p>
 * 账号本身不存组织归属（{@code sso_account} 只有账号信息），组织关系在 EHR 的员工档案里。
 * 「按组织树筛账号」这一步必须借 EHR 把「组织 → 员工 → 账号」翻译一次，
 * 走内部令牌的 Feign 调用，不做跨库 JOIN——生产上两个服务可能不在同一个 MySQL 实例。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EhrOrgClient {

    /** 组织反查账号集合的本地缓存：概览等高频读场景 60s 内复用，避免每次 Feign 拉取全量账号 ID。 */
    private static final long CODE_CACHE_TTL_MS = 60_000L;

    private final EhrOrgApi ehrOrgApi;
    private final Map<String, CodeCacheEntry> codeCache = new ConcurrentHashMap<>();

    /**
     * 该组织及所有下级组织下的账号 ID。
     * <p>
     * 空列表是合法结果（组织下还没人开户），调用方据此返回空页而不是「忽略过滤条件显示全部」——
     * 否则选一个空组织却看到全公司账号，比报错更误导人。
     */
    public List<Long> accountIds(Long orgId) {
        try {
            R<List<String>> resp = ehrOrgApi.accountIds(orgId);
            if (resp == null) {
                return List.of();
            }
            if (!resp.ok()) {
                String msg = resp.getMessage() == null ? "组织服务返回错误" : resp.getMessage();
                log.warn("EHR 返回业务错误：orgId={} msg={}", orgId, msg);
                throw new BizException(ErrorCode.SYSTEM_ERROR, msg);
            }
            List<Long> out = new ArrayList<>();
            if (resp.getData() != null) {
                for (String s : resp.getData()) {
                    if (s != null && !s.isBlank() && !"null".equalsIgnoreCase(s)) {
                        try {
                            out.add(Long.valueOf(s.trim()));
                        } catch (NumberFormatException ignored) {
                            // 脏数据不该让整个列表打不开，跳过即可
                        }
                    }
                }
            }
            return out;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("按组织反查账号失败：orgId={} err={}", orgId, e.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "组织服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 该组织编码（scopeCode）下所有账号 ID。
     * 走 Feign 内部 API 按 org_code LIKE 查 EHR 员工 → 账号列表。
     */
    public List<Long> accountIdsByCode(String scopeCode) {
        if (scopeCode == null || scopeCode.isBlank()) return List.of();
        long now = System.currentTimeMillis();
        CodeCacheEntry cached = codeCache.get(scopeCode);
        if (cached != null && now < cached.expireAt) return cached.ids;
        List<Long> out = fetchAccountIdsByCode(scopeCode);
        // 只缓存成功且非空的结果；失败/空结果不写缓存，下次仍重试（与失败返回空的语义一致）
        if (!out.isEmpty()) {
            codeCache.put(scopeCode, new CodeCacheEntry(out, now + CODE_CACHE_TTL_MS));
        }
        return out;
    }

    private List<Long> fetchAccountIdsByCode(String scopeCode) {
        try {
            R<List<String>> resp = ehrOrgApi.accountIdsByCode(scopeCode);
            if (resp == null || !resp.ok()) {
                return List.of();
            }
            List<Long> out = new ArrayList<>();
            if (resp.getData() != null) {
                for (String s : resp.getData()) {
                    if (s != null && !s.isBlank()) {
                        try { out.add(Long.valueOf(s.trim())); } catch (NumberFormatException ignored) {}
                    }
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("按组织编码反查账号失败：scopeCode={} err={}", scopeCode, e.getMessage());
            return List.of();
        }
    }

    private record CodeCacheEntry(List<Long> ids, long expireAt) {}
}
