package com.laimengchao.sso.cms.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.DataScope;
import com.laimengchao.sso.common.security.ScopeCodeCache;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * CMS 模块的数据权限服务。实现 common 的 {@link DataScope} 接口。
 */
@Service
@RequiredArgsConstructor
public class DataScopeService implements DataScope {

    private final ScopeCodeCache scopeCodeCache;

    @Override
    public String currentScopeCode() {
        Long accountId = AccountContext.getAccountId();
        return accountId == null ? null : scopeCodeCache.get(accountId);
    }

    @Override
    public String scopeCodeOf(Long accountId) {
        return scopeCodeCache.get(accountId);
    }

    /** 把数据范围拼进任意业务单据的 LambdaQueryWrapper */
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
                    if (i == 0) w.likeRight(orgCodeFn, r);
                    else w.or().likeRight(orgCodeFn, r);
                }
            });
        }
    }

    public boolean canAccessOrg(String orgCode) {
        return DataScope.super.canAccessOrg(orgCode);
    }

    public void assertCanAccessOrg(String orgCode) {
        if (!canAccessOrg(orgCode)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该组织节点");
        }
    }
}
