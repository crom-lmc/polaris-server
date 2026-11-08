package com.laimengchao.sso.account.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 服务间开户请求（仅 /internal/accounts/open 使用，凭 X-Internal-Token 准入）。
 * <p>
 * 登录名与密码都可以留空：留空时由账户域按邮箱/手机号生成唯一登录名、随机生成初始密码，
 * 业务方（如 EHR）不重复实现账户规则。
 */
@Data
public class InternalOpenAccountRequest {

    /** 期望登录名；为空则按邮箱前缀 → 手机号 → 随机串依次生成 */
    private String username;
    private String nickname;
    private String email;
    private String mobile;
    /** 留空则随机生成 12 位（满足密码策略） */
    private String password;
    /** 需要开通的应用编码，如 sso-center / portal / ehr */
    private List<String> appCodes;
    /** 需要授予的角色编码，如 EHR_BASIC / EHR_LEAVE */
    private List<String> roleCodes;
    /** 账户来源标记，默认 ehr */
    private String source;
}
