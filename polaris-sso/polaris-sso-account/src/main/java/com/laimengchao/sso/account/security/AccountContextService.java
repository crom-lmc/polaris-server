package com.laimengchao.sso.account.security;

import com.laimengchao.sso.account.constant.PermissionConstants;
import com.laimengchao.sso.account.mapper.SsoAccountMapper;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * 构建并填充 {@link AccountContext}。供 LoginContextInterceptor 在请求入口调用。
 * 超级管理员角色（SUPER_ADMIN）标记 isSuperAdmin，后续 @Permit 校验直接放行。
 */
@Service
@RequiredArgsConstructor
public class AccountContextService {

    /** 内置超级管理员角色编码 */
    public static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    private final SsoAccountMapper accountMapper;

    /**
     * 直接采用网关验签后的结论填充上下文：不查库，零额外开销。
     * 权限码来自 token 快照，授权回收后要等 token 过期（最长 access TTL）才生效——
     * 需要「立即失权」时把这里换成按 accountId 查库并加缓存。
     */
    public void fillFromHeaders(Long accountId, String username, boolean superAdmin, Set<String> permissions) {
        fillFromHeaders(accountId, username, superAdmin, permissions, null);
    }

    /**
     * @param appCode 令牌所属应用（来自网关的 X-App-Code）；null 视为平台自身
     */
    public void fillFromHeaders(Long accountId, String username, boolean superAdmin,
                                Set<String> permissions, String appCode) {
        String app = (appCode == null || appCode.isBlank())
                ? PermissionConstants.PLATFORM_APP : appCode.trim();
        AccountContext.set(
                new AccountContext.AccountInfo(accountId, username, permissions, superAdmin, app));
    }

    public void fill(Long accountId) {
        var account = accountMapper.selectById(accountId);
        if (account == null) {
            return;
        }
        Set<String> roleCodes = new HashSet<>(accountMapper.selectRoleCodesByAccountId(accountId));
        boolean superAdmin = roleCodes.contains(SUPER_ADMIN_ROLE);
        // account 服务只承载平台自身（sso）的接口，因此兜底查询固定按 sso 应用过滤：
        // 业务应用的按钮码不属于这里，混进来会让 @Permit 在某些码上误判通过
        Set<String> permissions = superAdmin
                ? new HashSet<>(accountMapper.selectAllPermissionCodes(PermissionConstants.PLATFORM_APP))
                : new HashSet<>(accountMapper.selectPermissionCodesByAccountId(
                        accountId, PermissionConstants.PLATFORM_APP));
        AccountContext.AccountInfo info = new AccountContext.AccountInfo(accountId, account.getUsername(),
                permissions, superAdmin, PermissionConstants.PLATFORM_APP);
        AccountContext.set(info);
    }
}
