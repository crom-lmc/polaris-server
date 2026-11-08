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

    private final EhrOrgApi ehrOrgApi;

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
}
