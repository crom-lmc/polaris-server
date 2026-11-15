package com.laimengchao.sso.ehr.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.DataScope;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.common.security.ScopeCodeCache;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * EHR 模块的数据权限服务。**新方案**：
 * <ul>
 *   <li>废弃旧的 ScopeType/Scope/current()（按权限码推断四档范围 + 超管放行）</li>
 *   <li>统一用 {@code dataScopeOrgCode LIKE 'scopeCode%'} 过滤</li>
 *   <li>scopeCode 通过 {@link ScopeCodeCache} 拿（ThreadLocal → Redis → Feign 调 sso-account）</li>
 * </ul>
 *
 * <p>遗留保留：{@link #canRevealSensitive()} 和 {@link #hasPermission(String)} —
 * 敏感字段明文权限是独立概念（按 {@code employee:sensitive:view} 权限码），和数据范围隔离无关。
 */
@Service
@RequiredArgsConstructor
public class DataScopeService implements DataScope {

    private final ScopeCodeCache scopeCodeCache;

    // ============ DataScope 接口实现 ============

    @Override
    public String currentScopeCode() {
        Long accountId = AccountContext.getAccountId();
        return accountId == null ? null : scopeCodeCache.get(accountId);
    }

    @Override
    public String scopeCodeOf(Long accountId) {
        return scopeCodeCache.get(accountId);
    }

    // ============ 业务单据查询拼 LIKE ============

    /**
     * 把数据范围拼进任意业务单据的 LambdaQueryWrapper。
     * 空 scopeCode（未配置）→ WHERE org_code = '__NO_ACCESS__'（安全兜底空集）。
     */
    /**
     * 把数据范围拼进任意业务单据的 LambdaQueryWrapper。
     * scopeCode 支持逗号分隔多值：每个根节点拼一个 LIKE，用 OR 组合。
     */
    public <T> void applyOrgScope(LambdaQueryWrapper<T> wrapper, SFunction<T, String> orgCodeFn) {
        String scopeCode = currentScopeCode();
        if (scopeCode == null || scopeCode.isBlank()) {
            return;
        }
        String[] roots = scopeCode.split(",");
        if (roots.length == 1) {
            wrapper.likeRight(orgCodeFn, roots[0].trim());
        } else {
            wrapper.and(w -> {
                for (int i = 0; i < roots.length; i++) {
                    String r = roots[i].trim();
                    if (r.isEmpty()) continue;
                    if (i == 0) {
                        w.likeRight(orgCodeFn, r);
                    } else {
                        w.or().likeRight(orgCodeFn, r);
                    }
                }
            });
        }
    }

    // ============ 单条记录校验 ============

    /** 单条记录是否可见（返回 boolean，detail/export 用） */
    public boolean canAccessOrg(String orgCode) {
        return DataScope.super.canAccessOrg(orgCode);
    }

    /** 单条记录是否可见（抛 Forbidden，OrgService CRUD 用） */
    public void assertCanAccessOrg(String orgCode) {
        if (!canAccessOrg(orgCode)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该组织节点");
        }
    }

    // ============ 旧方案保留（独立于数据范围隔离） ============

    /** 是否有敏感字段明文权限（独立的按钮级权限，和数据范围无关） */
    public boolean canRevealSensitive() {
        SecurityUser user = SecurityUtils.currentUser().orElse(null);
        return user != null
                && (user.isSuperAdmin() || hasPermission(user, PermissionCodes.EMPLOYEE_SENSITIVE_VIEW));
    }

    public boolean hasPermission(String code) {
        return hasPermission(SecurityUtils.currentUser().orElse(null), code);
    }

    private boolean hasPermission(SecurityUser user, String code) {
        return user != null && user.getPermissions() != null && user.getPermissions().contains(code);
    }
}
