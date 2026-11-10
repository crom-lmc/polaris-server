package com.laimengchao.sso.account.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.account.constant.PermissionConstants;
import com.laimengchao.sso.account.domain.entity.SsoAccount;
import com.laimengchao.sso.account.domain.entity.SsoAccountApp;
import com.laimengchao.sso.account.domain.entity.SsoAccountRole;
import com.laimengchao.sso.account.domain.entity.SsoApplication;
import com.laimengchao.sso.account.domain.entity.SsoRole;
import com.laimengchao.sso.account.dto.request.CreateAccountRequest;
import com.laimengchao.sso.account.dto.request.InternalOpenAccountRequest;
import com.laimengchao.sso.account.dto.request.ResetPasswordRequest;
import com.laimengchao.sso.account.dto.request.UpdateAccountRequest;
import com.laimengchao.sso.account.dto.response.AccountVO;
import com.laimengchao.sso.account.dto.response.LoginResult;
import com.laimengchao.sso.account.dto.response.OpenAccountResult;
import com.laimengchao.sso.account.integration.EhrOrgClient;
import com.laimengchao.sso.account.mapper.SsoAccountAppMapper;
import com.laimengchao.sso.account.mapper.SsoAccountMapper;
import com.laimengchao.sso.account.mapper.SsoAccountRoleMapper;
import com.laimengchao.sso.account.mapper.SsoApplicationMapper;
import com.laimengchao.sso.account.mapper.SsoRoleMapper;
import com.laimengchao.sso.account.security.AccountContextService;
import com.laimengchao.sso.account.security.PasswordPolicy;
import com.laimengchao.sso.account.service.AccountService;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.common.enums.AccountStatus;
import com.laimengchao.sso.utils.Asserts;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final SsoAccountMapper accountMapper;
    private final SsoAccountRoleMapper accountRoleMapper;
    private final SsoRoleMapper roleMapper;
    private final SsoApplicationMapper applicationMapper;
    private final SsoAccountAppMapper accountAppMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final EhrOrgClient ehrOrgClient;

    @Value("${sso.security.max-login-fail:5}")
    private int maxLoginFail;
    @Value("${sso.security.lock-minutes:30}")
    private int lockMinutes;

    @Override
    public PageResult<AccountVO> page(int page, int size, String keyword, Long orgId) {
        LambdaQueryWrapper<SsoAccount> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            // nested 必须包起来：否则后面再拼 orgId 的 in() 时，SQL 会变成
            // username LIKE ? OR nickname LIKE ? OR email LIKE ? AND id IN (...)，
            // AND 优先级更高，组织过滤就只作用于最后一个 like 了。
            w.nested(q -> q.like(SsoAccount::getUsername, kw)
                    .or().like(SsoAccount::getNickname, kw)
                    .or().like(SsoAccount::getEmail, kw));
        }
        if (orgId != null) {
            List<Long> ids = ehrOrgClient.accountIds(orgId);
            if (ids.isEmpty()) {
                // 空组织 = 没有账号，绝不能退化成「不过滤显示全部」
                return PageResult.of(List.of(), 0, page, size);
            }
            w.in(SsoAccount::getId, ids);
        }
        w.orderByDesc(SsoAccount::getCreatedAt);
        IPage<SsoAccount> ip = accountMapper.selectPage(new Page<>(page, size), w);
        List<AccountVO> records = ip.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(records, ip.getTotal(), page, size);
    }

    @Override
    public AccountVO detail(Long id) {
        SsoAccount a = accountMapper.selectById(id);
        Asserts.notNull(a, ErrorCode.NOT_FOUND, "账户不存在");
        return toVO(a);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AccountVO create(CreateAccountRequest req) {
        Asserts.notEmpty(req.getUsername(), ErrorCode.PARAM_ERROR, "登录名必填");
        Asserts.notEmpty(req.getPassword(), ErrorCode.PARAM_ERROR, "密码必填");
        PasswordPolicy.validate(req.getPassword());
        Asserts.isTrue(req.getUsername().matches("^[a-zA-Z0-9_]{3,32}$"),
                ErrorCode.PARAM_ERROR, "登录名需 3-32 位字母/数字/下划线");
        Asserts.isFalse(accountMapper.selectByUsername(req.getUsername()) != null,
                ErrorCode.CONFLICT, "登录名已存在");
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            Asserts.isFalse(accountMapper.selectByEmail(req.getEmail()) != null,
                    ErrorCode.CONFLICT, "邮箱已存在");
        }

        SsoAccount a = new SsoAccount();
        a.setUsername(req.getUsername());
        a.setNickname(req.getNickname() == null ? req.getUsername() : req.getNickname());
        // 邮箱为空时存 NULL（而非 ""），否则 uk_email 唯一索引会因多个空串冲突
        a.setEmail(req.getEmail() == null || req.getEmail().isBlank() ? null : req.getEmail());
        a.setPhone(req.getPhone() == null ? "" : req.getPhone());
        a.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        a.setStatus(AccountStatus.NORMAL.getCode());
        a.setSource("local");
        a.setPwdResetRequired(0);
        a.setLoginFailCount(0);
        accountMapper.insert(a);

        if (req.getRoleIds() != null && !req.getRoleIds().isEmpty()) {
            linkRoles(a.getId(), req.getRoleIds());
        }
        return toVO(a);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AccountVO update(Long id, UpdateAccountRequest req) {
        SsoAccount a = accountMapper.selectById(id);
        Asserts.notNull(a, ErrorCode.NOT_FOUND, "账户不存在");

        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            Asserts.isTrue(accountMapper.countEmailExceptId(req.getEmail(), id) == 0,
                    ErrorCode.CONFLICT, "邮箱已存在");
            a.setEmail(req.getEmail());
        }
        if (req.getNickname() != null) {
            a.setNickname(req.getNickname());
        }
        if (req.getPhone() != null) {
            a.setPhone(req.getPhone());
        }
        accountMapper.updateById(a);

        accountRoleMapper.delete(new LambdaQueryWrapper<SsoAccountRole>()
                .eq(SsoAccountRole::getAccountId, id));
        if (req.getRoleIds() != null && !req.getRoleIds().isEmpty()) {
            linkRoles(id, req.getRoleIds());
        }
        return toVO(a);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SsoAccount a = accountMapper.selectById(id);
        Asserts.notNull(a, ErrorCode.NOT_FOUND, "账户不存在");
        Asserts.isFalse(isSuperAdminAccount(id), ErrorCode.FORBIDDEN, "超级管理员账户不可删除");
        accountMapper.deleteById(id);
        accountRoleMapper.delete(new LambdaQueryWrapper<SsoAccountRole>()
                .eq(SsoAccountRole::getAccountId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(Long id) {
        SsoAccount a = require(id);
        a.setStatus(AccountStatus.NORMAL.getCode());
        a.setLockedUntil(null);
        a.setLoginFailCount(0);
        accountMapper.updateById(a);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        SsoAccount a = require(id);
        Asserts.isFalse(isSuperAdminAccount(id), ErrorCode.FORBIDDEN, "超级管理员账户不可禁用");
        a.setStatus(AccountStatus.DISABLED.getCode());
        accountMapper.updateById(a);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setEnabled(Long id, boolean enabled) {
        SsoAccount a = require(id);
        if (enabled) {
            // 复职同时清掉锁定与失败计数：否则上一次锁定还会继续挡着
            a.setStatus(AccountStatus.NORMAL.getCode());
            a.setLockedUntil(null);
            a.setLoginFailCount(0);
        } else {
            Asserts.isFalse(isSuperAdminAccount(id), ErrorCode.FORBIDDEN, "超级管理员账户不可禁用");
            a.setStatus(AccountStatus.DISABLED.getCode());
        }
        accountMapper.updateById(a);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlock(Long id) {
        SsoAccount a = require(id);
        a.setStatus(AccountStatus.NORMAL.getCode());
        a.setLockedUntil(null);
        a.setLoginFailCount(0);
        accountMapper.updateById(a);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, ResetPasswordRequest req) {
        SsoAccount a = require(id);
        Asserts.notEmpty(req.getNewPassword(), ErrorCode.PARAM_ERROR, "新密码必填");
        PasswordPolicy.validate(req.getNewPassword());
        a.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        a.setPwdResetRequired(req.getForceChange() == null || req.getForceChange() ? 1 : 0);
        a.setLoginFailCount(0);
        a.setLockedUntil(null);
        accountMapper.updateById(a);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeMyPassword(Long id, String oldPassword, String newPassword) {
        SsoAccount a = accountMapper.selectById(id);
        Asserts.notNull(a, ErrorCode.NOT_FOUND, "账户不存在");
        Asserts.notEmpty(oldPassword, ErrorCode.PARAM_ERROR, "原密码必填");
        Asserts.notEmpty(newPassword, ErrorCode.PARAM_ERROR, "新密码必填");
        // 与登录失败锁定解耦：原密码错误只报错，不累加锁定计数
        if (!passwordEncoder.matches(oldPassword, a.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "原密码错误");
        }
        PasswordPolicy.validate(newPassword);
        a.setPasswordHash(passwordEncoder.encode(newPassword));
        // 改密后视为已满足改密要求，并清空失败计数/锁定，避免被旧锁定态卡住
        a.setPwdResetRequired(0);
        a.setLoginFailCount(0);
        a.setLockedUntil(null);
        accountMapper.updateById(a);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPasswordByAccount(Long id, String newPassword) {
        SsoAccount a = require(id);
        Asserts.notEmpty(newPassword, ErrorCode.PARAM_ERROR, "新密码必填");
        PasswordPolicy.validate(newPassword);
        a.setPasswordHash(passwordEncoder.encode(newPassword));
        a.setPwdResetRequired(0);
        a.setLoginFailCount(0);
        a.setLockedUntil(null);
        accountMapper.updateById(a);
    }

    @Override
    public SsoAccount authenticate(String username, String password) {
        // 登录名与邮箱都可作为账号标识：先按登录名查，查不到再按邮箱查（如 yangjinglei / yangjinglei@xckj.com）
        SsoAccount a = accountMapper.selectByUsername(username);
        if (a == null) {
            a = accountMapper.selectByEmail(username);
        }
        if (a == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        // 锁定判定
        if (a.getStatus() != null && a.getStatus() == AccountStatus.LOCKED.getCode()) {
            throw new BizException(ErrorCode.FORBIDDEN, "账户已锁定，请联系管理员解锁");
        }
        if (a.getStatus() != null && a.getStatus() == AccountStatus.DISABLED.getCode()) {
            throw new BizException(ErrorCode.FORBIDDEN, "账户已被禁用");
        }
        if (!passwordEncoder.matches(password, a.getPasswordHash())) {
            int fails = (a.getLoginFailCount() == null ? 0 : a.getLoginFailCount()) + 1;
            a.setLoginFailCount(fails);
            if (fails >= maxLoginFail) {
                a.setStatus(AccountStatus.LOCKED.getCode());
                a.setLockedUntil(LocalDateTime.now().plusMinutes(lockMinutes));
            }
            accountMapper.updateById(a);
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        // 成功：清零失败计数
        a.setLoginFailCount(0);
        a.setLastLoginAt(LocalDateTime.now());
        if (a.getStatus() != null && a.getStatus() == AccountStatus.LOCKED.getCode()) {
            a.setStatus(AccountStatus.NORMAL.getCode());
            a.setLockedUntil(null);
        }
        accountMapper.updateById(a);
        return a;
    }

    @Override
    public LoginResult buildLoginResult(Long accountId) {
        return buildLoginResult(accountId, PermissionConstants.PLATFORM_APP);
    }

    @Override
    public LoginResult buildLoginResult(Long accountId, String appCode) {
        SsoAccount a = accountMapper.selectById(accountId);
        Asserts.notNull(a, ErrorCode.NOT_FOUND, "账户不存在");
        String app = (appCode == null || appCode.isBlank())
                ? PermissionConstants.PLATFORM_APP : appCode.trim();
        // 应用写错时宁可报错，也不要签出一个「什么权限都没有」的令牌——那种故障最难查
        if (!PermissionConstants.PLATFORM_APP.equals(app)) {
            Asserts.notNull(applicationMapper.selectByCode(app), ErrorCode.NOT_FOUND,
                    "应用不存在：" + app);
        }
        List<String> roleCodes = accountMapper.selectRoleCodesByAccountId(accountId);
        boolean superAdmin = roleCodes.contains(AccountContextService.SUPER_ADMIN_ROLE);
        // 权限码按应用切分：超管拿该应用的全部码，普通账号拿角色在该应用下授权的码
        List<String> permissions = superAdmin
                ? accountMapper.selectAllPermissionCodes(app)
                : accountMapper.selectPermissionCodesByAccountId(accountId, app);
        // 可访问应用：超管视作全部启用应用；普通账号查授权表（已停用的应用不签发）
        List<String> apps = superAdmin
                ? applicationMapper.selectEnabledCodes()
                : accountAppMapper.selectEnabledCodesByAccount(accountId);

        LoginResult r = new LoginResult();
        r.setAccountId(a.getId());
        r.setUsername(a.getUsername());
        r.setNickname(a.getNickname());
        r.setSuperAdmin(superAdmin);
        r.setAppCode(app);
        r.setPermissions(permissions);
        r.setApps(apps);
        return r;
    }

    // ---------------- 内部工具 ----------------

    @Override
    public LoginResult principal(Long accountId) {
        return principal(accountId, PermissionConstants.PLATFORM_APP);
    }

    @Override
    public LoginResult principal(Long accountId, String appCode) {
        SsoAccount a = require(accountId);
        if (a.getStatus() != null && a.getStatus() == AccountStatus.LOCKED.getCode()) {
            throw new BizException(ErrorCode.FORBIDDEN, "账户已锁定");
        }
        if (a.getStatus() != null && a.getStatus() == AccountStatus.DISABLED.getCode()) {
            throw new BizException(ErrorCode.FORBIDDEN, "账户已被禁用");
        }
        return buildLoginResult(accountId, appCode);
    }

    /**
     * 服务间开户（EHR 创建人员时调用）。
     * <p>
     * 幂等策略：先按登录名、再按邮箱命中已有账号——HR 重复提交不该造出第二个账号；
     * 命中后只补授权，不重置密码（否则会把员工自己改过的密码冲掉）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenAccountResult openAccount(InternalOpenAccountRequest req) {
        String email = trimToNull(req.getEmail());
        String phone = req.getMobile() == null ? "" : req.getMobile().trim();
        String nickname = trimToNull(req.getNickname());

        SsoAccount account = null;
        String wantedUsername = trimToNull(req.getUsername());
        if (wantedUsername != null) {
            account = accountMapper.selectByUsername(wantedUsername);
        }
        if (account == null && email != null) {
            account = accountMapper.selectByEmail(email);
        }

        OpenAccountResult result = new OpenAccountResult();
        if (account == null) {
            String username = generateUniqueUsername(wantedUsername, email, phone);
            String password = trimToNull(req.getPassword());
            if (password == null) {
                password = randomPassword();
            } else {
                PasswordPolicy.validate(password);
            }
            // 邮箱可能已被别的登录名占用（uk_email 唯一索引），此时宁可不写邮箱也不要让开户失败
            String emailToUse = email != null && accountMapper.selectByEmail(email) == null ? email : null;

            SsoAccount a = new SsoAccount();
            a.setUsername(username);
            a.setNickname(nickname == null ? username : nickname);
            a.setEmail(emailToUse);
            a.setPhone(phone);
            a.setPasswordHash(passwordEncoder.encode(password));
            a.setStatus(AccountStatus.NORMAL.getCode());
            a.setSource(trimToNull(req.getSource()) == null ? "ehr" : req.getSource().trim());
            // 初始密码由系统生成时必须首登改密；调用方显式传密码的也保持同样要求
            a.setPwdResetRequired(1);
            a.setLoginFailCount(0);
            accountMapper.insert(a);
            account = a;
            result.setInitialPassword(password);
            result.setCreated(true);
        } else {
            result.setCreated(false);
            result.setNotice("已存在账号 " + account.getUsername() + "（按登录名/邮箱匹配），本次只补齐授权");
        }

        // 应用授权（幂等：已授权不重复插）
        for (String code : safeList(req.getAppCodes())) {
            SsoApplication app = applicationMapper.selectByCode(code);
            if (app == null) {
                continue;
            }
            Long exists = accountAppMapper.selectCount(new LambdaQueryWrapper<SsoAccountApp>()
                    .eq(SsoAccountApp::getAccountId, account.getId())
                    .eq(SsoAccountApp::getAppId, app.getId()));
            if (exists == null || exists == 0) {
                SsoAccountApp link = new SsoAccountApp();
                link.setAccountId(account.getId());
                link.setAppId(app.getId());
                accountAppMapper.insert(link);
            }
            result.getApps().add(app.getCode());
        }

        // 角色授权（幂等）
        for (String code : safeList(req.getRoleCodes())) {
            SsoRole role = roleMapper.selectByCode(code);
            if (role == null) {
                continue;
            }
            Long exists = accountRoleMapper.selectCount(new LambdaQueryWrapper<SsoAccountRole>()
                    .eq(SsoAccountRole::getAccountId, account.getId())
                    .eq(SsoAccountRole::getRoleId, role.getId()));
            if (exists == null || exists == 0) {
                SsoAccountRole link = new SsoAccountRole();
                link.setAccountId(account.getId());
                link.setRoleId(role.getId());
                accountRoleMapper.insert(link);
            }
            result.getRoles().add(role.getCode());
        }

        result.setAccountId(account.getId());
        result.setUsername(account.getUsername());
        return result;
    }

    /**
     * 生成登录名：有邮箱就直接用邮箱全地址（库内存量账号即此形态，登录接口也支持邮箱登录），
     * 没有邮箱时依次退化为期望登录名 → 手机号 → 随机串，逐个消解冲突（最多试 50 次）。
     */
    private String generateUniqueUsername(String wanted, String email, String phone) {
        String base = trimToNull(email);
        if (base == null) {
            base = trimToNull(wanted);
        }
        if (base != null) {
            base = base.replaceAll("\\s", "").toLowerCase();
            int at = base.indexOf('@');
            if (at > 0) {
                // 邮箱必须保留 @ 与域名：这里若截前缀或清洗掉 @，就会生成 laimengchao 这种短名
                String local = base.substring(0, at).replaceAll("[^a-z0-9_.+-]", "");
                String domain = base.substring(at + 1).replaceAll("[^a-z0-9.-]", "");
                base = local.isEmpty() || domain.isEmpty() ? null : local + "@" + domain;
            } else {
                base = base.replaceAll("[^a-z0-9_]", "");
            }
        }
        if (base == null || base.isEmpty()) {
            base = phone != null && !phone.isBlank() ? phone.replaceAll("[^0-9]", "") : "";
        }
        if (base.isEmpty()) {
            base = "u" + System.currentTimeMillis() % 1000000;
        }
        if (base.length() < 3) {
            base = "u" + base + "001";
        }
        if (base.length() > 64) {
            base = base.substring(0, 64);
        }
        String candidate = base;
        for (int i = 1; i <= 50 && accountMapper.selectByUsername(candidate) != null; i++) {
            candidate = base + i;
        }
        if (accountMapper.selectByUsername(candidate) != null) {
            candidate = base + (System.currentTimeMillis() % 10000);
        }
        return candidate;
    }

    /** 12 位随机初始密码：大小写+数字+特殊各取几位后打乱，天然满足「四类中至少三类」 */
    private String randomPassword() {
        final String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        final String lower = "abcdefghijkmnpqrstuvwxyz";
        final String digit = "23456789";
        // 密码策略黑名单排除了 & $ % ( ) 等元字符，这里只用安全特殊字符
        final String special = "@#^*!?";
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            sb.append(upper.charAt(random.nextInt(upper.length())));
            sb.append(lower.charAt(random.nextInt(lower.length())));
            sb.append(digit.charAt(random.nextInt(digit.length())));
            sb.append(special.charAt(random.nextInt(special.length())));
        }
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    private java.util.List<String> safeList(java.util.List<String> raw) {
        if (raw == null) {
            return java.util.List.of();
        }
        return raw.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).distinct().toList();
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private SsoAccount require(Long id) {
        SsoAccount a = accountMapper.selectById(id);
        Asserts.notNull(a, ErrorCode.NOT_FOUND, "账户不存在");
        return a;
    }

    private void linkRoles(Long accountId, List<Long> roleIds) {
        for (Long roleId : roleIds) {
            SsoAccountRole ar = new SsoAccountRole();
            ar.setAccountId(accountId);
            ar.setRoleId(roleId);
            accountRoleMapper.insert(ar);
        }
        grantAppsForRoles(accountId, roleIds);
    }

    /**
     * 授予角色时顺带开通角色所属应用。
     * <p>
     * 权限码只有在该应用的令牌里才会下发：账号只授权了 sso-center、却拿到 EHR 角色时，
     * 会表现为「角色明明给了，进 EHR 还是没菜单」。这里补上应用授权，让「授角色」和「能用哪个系统」一致；
     * 只增不减——改成别的角色不会把之前单独开通的应用收回去。
     */
    private void grantAppsForRoles(Long accountId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        Set<String> appCodes = roleMapper.selectBatchIds(roleIds).stream()
                .map(SsoRole::getAppCode)
                .filter(c -> c != null && !c.isBlank() && !"sso".equals(c))
                .collect(Collectors.toSet());
        if (appCodes.isEmpty()) {
            return;
        }
        List<SsoApplication> apps = applicationMapper.selectList(
                new LambdaQueryWrapper<SsoApplication>().in(SsoApplication::getCode, appCodes));
        for (SsoApplication app : apps) {
            Long exists = accountAppMapper.selectCount(new LambdaQueryWrapper<SsoAccountApp>()
                    .eq(SsoAccountApp::getAccountId, accountId)
                    .eq(SsoAccountApp::getAppId, app.getId()));
            if (exists == null || exists == 0) {
                SsoAccountApp link = new SsoAccountApp();
                link.setAccountId(accountId);
                link.setAppId(app.getId());
                accountAppMapper.insert(link);
            }
        }
    }

    private List<String> getRoleCodes(Long accountId) {
        List<Long> roleIds = accountRoleMapper.selectList(
                        new LambdaQueryWrapper<SsoAccountRole>().eq(SsoAccountRole::getAccountId, accountId))
                .stream().map(SsoAccountRole::getRoleId).collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            return new ArrayList<>();
        }
        return roleMapper.selectBatchIds(roleIds).stream()
                .map(SsoRole::getCode).collect(Collectors.toList());
    }

    private boolean isSuperAdminAccount(Long accountId) {
        return getRoleCodes(accountId).contains(AccountContextService.SUPER_ADMIN_ROLE);
    }

    private Long adminAccountId() {
        SsoAccount a = accountMapper.selectByUsername("admin");
        return a == null ? -1L : a.getId();
    }

    private AccountVO toVO(SsoAccount a) {
        AccountVO vo = new AccountVO();
        vo.setId(a.getId());
        vo.setUsername(a.getUsername());
        vo.setNickname(a.getNickname());
        vo.setEmail(a.getEmail());
        vo.setPhone(a.getPhone());
        vo.setStatus(a.getStatus());
        vo.setStatusDesc(a.getStatus() == null ? ""
                : AccountStatus.of(a.getStatus()).getDesc());
        vo.setSource(a.getSource());
        vo.setPwdResetRequired(a.getPwdResetRequired());
        vo.setLastLoginAt(a.getLastLoginAt());
        vo.setCreatedAt(a.getCreatedAt());
        vo.setRoles(getRoleCodes(a.getId()));
        return vo;
    }
}
