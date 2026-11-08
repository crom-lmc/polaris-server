package com.laimengchao.sso.ehr.integration;

import com.laimengchao.sso.client.api.AccountApi;
import com.laimengchao.sso.client.dto.AccountStatusRequest;
import com.laimengchao.sso.client.dto.OpenAccountRequest;
import com.laimengchao.sso.client.dto.OpenAccountResult;
import com.laimengchao.sso.client.dto.R;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 统一账号中心的内部开户客户端。
 * <p>
 * 走 polaris-sso-client 的 {@link AccountApi}（不经网关），凭 {@code X-Internal-Token} 准入；
 * 接口幂等：同名账号已存在时复用并补齐应用/角色授权，返回 created=false。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountClient {

    private final AccountApi accountApi;

    @Value("${sso.client.internal-token:${SSO_INTERNAL_TOKEN:}}")
    private String internalToken;

    @Value("${ehr.account.default-apps:sso-center,portal,ehr}")
    private String defaultApps;

    @Value("${ehr.account.default-roles:EHR_BASIC}")
    private String defaultRoles;

    public List<String> defaultApps() {
        return split(defaultApps);
    }

    public List<String> defaultRoles() {
        return split(defaultRoles);
    }

    /**
     * 开户。失败抛 {@link EhrErrorCode#ACCOUNT_OPEN_FAILED}，由调用方决定是否回滚本地事务。
     */
    public AccountDtos.OpenResult open(String username, String nickname, String email,
                                       String mobile, String password) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new BizException(EhrErrorCode.ACCOUNT_OPEN_FAILED,
                    "本服务未配置内部调用令牌，无法开户");
        }
        OpenAccountRequest req = new OpenAccountRequest();
        req.setUsername(username);
        req.setNickname(nickname);
        req.setEmail(email);
        req.setMobile(mobile);
        req.setPassword(password);
        req.setAppCodes(defaultApps());
        req.setRoleCodes(defaultRoles());

        try {
            R<OpenAccountResult> resp = accountApi.open(req);
            if (resp == null) {
                throw new BizException(EhrErrorCode.ACCOUNT_OPEN_FAILED, "账号中心无响应");
            }
            if (!resp.ok()) {
                throw new BizException(EhrErrorCode.ACCOUNT_OPEN_FAILED,
                        resp.getMessage() == null ? "开户失败" : resp.getMessage());
            }
            OpenAccountResult data = resp.getData();
            if (data == null) {
                throw new BizException(EhrErrorCode.ACCOUNT_OPEN_FAILED, "账号中心无响应");
            }
            AccountDtos.OpenResult result = new AccountDtos.OpenResult();
            result.setAccountId(data.getAccountId());
            result.setUsername(data.getUsername() == null ? "" : data.getUsername());
            result.setInitialPassword(data.getInitialPassword());
            result.setCreated(data.isCreated());
            result.setApps(data.getApps());
            result.setRoles(data.getRoles());
            log.info("开户成功：username={} created={} apps={} roles={}",
                    result.getUsername(), result.isCreated(), result.getApps(), result.getRoles());
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用账号中心开户失败", e);
            throw new BizException(EhrErrorCode.ACCOUNT_OPEN_FAILED,
                    "账号中心暂不可用，人员未创建");
        }
    }

    /**
     * 启停账号（离职 / 复职联动）。
     * <p>
     * 失败直接抛异常：调用方在本事务里改员工状态，抛出去才能一起回滚，
     * 不会留下「档案已离职、账号还能登」的半截数据。
     */
    public void setEnabled(Long accountId, boolean enabled) {
        if (accountId == null) {
            return;
        }
        if (internalToken == null || internalToken.isBlank()) {
            throw new BizException(EhrErrorCode.ACCOUNT_STATUS_FAILED,
                    "本服务未配置内部调用令牌，无法同步账号状态");
        }
        try {
            AccountStatusRequest req = new AccountStatusRequest();
            req.setEnabled(enabled);
            R<Void> resp = accountApi.changeStatus(accountId, req);
            if (resp == null) {
                throw new BizException(EhrErrorCode.ACCOUNT_STATUS_FAILED, "账号中心无响应");
            }
            if (!resp.ok()) {
                throw new BizException(EhrErrorCode.ACCOUNT_STATUS_FAILED,
                        resp.getMessage() == null ? "账号状态同步失败" : resp.getMessage());
            }
            log.info("账号状态已同步：accountId={} enabled={}", accountId, enabled);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用账号中心同步账号状态失败", e);
            throw new BizException(EhrErrorCode.ACCOUNT_STATUS_FAILED,
                    "账号中心暂不可用，员工状态未变更");
        }
    }

    private List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
