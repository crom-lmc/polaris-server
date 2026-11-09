package com.laimengchao.sso.account.service;

import com.laimengchao.sso.account.dto.request.CreateAccountRequest;
import com.laimengchao.sso.account.dto.request.InternalOpenAccountRequest;
import com.laimengchao.sso.account.dto.request.ResetPasswordRequest;
import com.laimengchao.sso.account.dto.request.UpdateAccountRequest;
import com.laimengchao.sso.account.dto.response.AccountVO;
import com.laimengchao.sso.account.dto.response.LoginResult;
import com.laimengchao.sso.account.dto.response.OpenAccountResult;
import com.laimengchao.sso.account.domain.entity.SsoAccount;
import com.laimengchao.sso.common.PageResult;

public interface AccountService {

    /**
     * 分页查询账号。
     *
     * @param orgId 组织树筛选：该组织及所有下级组织下的账号；null 表示不过滤
     */
    PageResult<AccountVO> page(int page, int size, String keyword, Long orgId);

    AccountVO detail(Long id);

    AccountVO create(CreateAccountRequest req);

    AccountVO update(Long id, UpdateAccountRequest req);

    void delete(Long id);

    void enable(Long id);

    void disable(Long id);

    /** 服务间启停账号：EHR 办离职时停用、复职时启用，避免离职后还能登录各系统 */
    void setEnabled(Long id, boolean enabled);

    void unlock(Long id);

    void resetPassword(Long id, ResetPasswordRequest req);

    /** 当前登录者修改自己的密码（校验原密码）。 */
    void changeMyPassword(Long id, String oldPassword, String newPassword);

    /** 忘记密码：验证码已由 auth 校验通过，按账号直接重置密码。 */
    void resetPasswordByAccount(Long id, String newPassword);

    /** 校验凭据，返回账户（失败抛异常）。 */
    SsoAccount authenticate(String username, String password);

    /** 构建登录结果（含权限码，供前端按钮级渲染）。appCode 缺省为平台自身 sso。 */
    LoginResult buildLoginResult(Long accountId);

    /** 构建指定应用范围内的登录结果：permissions 只含该应用的权限码。 */
    LoginResult buildLoginResult(Long accountId, String appCode);

    /**
     * 取账户身份快照，供 sso-auth 刷新令牌时复用。
     * 与 buildLoginResult 的差别：会校验账户状态，禁用/锁定/已删除一律拒绝。
     */
    LoginResult principal(Long accountId);

    /** 取指定应用范围内的身份快照（业务应用兑换 ticket 时用）。 */
    LoginResult principal(Long accountId, String appCode);

    /**
     * 服务间开户：新建或复用账户，并补齐应用与角色授权（幂等）。
     * <p>
     * 登录名/初始密码留空时由账户域生成——唯一性、密码策略、来源标记都只在这里实现一份。
     */
    OpenAccountResult openAccount(InternalOpenAccountRequest req);
}
