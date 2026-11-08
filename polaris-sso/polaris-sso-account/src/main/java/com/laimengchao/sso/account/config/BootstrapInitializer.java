package com.laimengchao.sso.account.config;

import com.laimengchao.sso.account.constant.PermissionConstants;
import com.laimengchao.sso.account.constant.PermissionConstants.PermDef;
import com.laimengchao.sso.account.domain.entity.SsoAccount;
import com.laimengchao.sso.account.domain.entity.SsoAccountRole;
import com.laimengchao.sso.account.domain.entity.SsoApplication;
import com.laimengchao.sso.account.domain.entity.SsoPermission;
import com.laimengchao.sso.account.domain.entity.SsoRole;
import com.laimengchao.sso.account.domain.entity.SsoRolePermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.account.mapper.SsoAccountMapper;
import com.laimengchao.sso.account.mapper.SsoAccountRoleMapper;
import com.laimengchao.sso.account.mapper.SsoApplicationMapper;
import com.laimengchao.sso.account.mapper.SsoPermissionMapper;
import com.laimengchao.sso.account.mapper.SsoRoleMapper;
import com.laimengchao.sso.account.mapper.SsoRolePermissionMapper;
import com.laimengchao.sso.account.security.AccountContextService;
import com.laimengchao.sso.account.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动初始化：内置超级管理员账户 + SUPER_ADMIN 角色 + 全部内置权限。
 * 「存在则跳过」，重复启动安全。生产请通过环境变量修改默认口令。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapInitializer implements ApplicationRunner {

    private final SsoAccountMapper accountMapper;
    private final SsoRoleMapper roleMapper;
    private final SsoPermissionMapper permissionMapper;
    private final SsoAccountRoleMapper accountRoleMapper;
    private final SsoRolePermissionMapper rolePermissionMapper;
    private final SsoApplicationMapper applicationMapper;
    private final JdbcTemplate jdbcTemplate;

    @Value("${sso.bootstrap.admin-username:admin}")
    private String adminUsername;
    @Value("${sso.bootstrap.admin-password:Admin@123}")
    private String adminPassword;
    @Value("${sso.bootstrap.admin-email:admin@laimengchao.site}")
    private String adminEmail;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public void run(ApplicationArguments args) {
        migrateSchema();
        initPermissions();
        initNoticeRoleBindings();
        initSuperAdmin();
        initBuiltinApplication();
    }

    /**
     * 播种内置应用「统一账号中心」。幂等：存在则跳过。
     *
     * <p>它是平台自身在「接入应用」列表里的常驻条目，同时堵住别人用 sso-center
     * 这个编码注册应用（服务层 create 也做了保留字校验）。app_secret 列 NOT NULL，
     * 随机生成一个即可——它不参与任何 exchange 流程，也没人知道它是什么。
     */
    private void initBuiltinApplication() {
        if (applicationMapper.selectByCode(ApplicationService.BUILTIN_CODE) != null) {
            return;
        }
        SsoApplication app = new SsoApplication();
        app.setCode(ApplicationService.BUILTIN_CODE);
        app.setName("统一账号中心");
        app.setBaseUrl("");
        app.setRemark("平台内置应用，代表平台自身，不可编辑或删除");
        app.setStatus(1);
        app.setSort(-1);
        app.setAppSecret(randomSecret());
        applicationMapper.insert(app);
        log.info("已播种内置应用：{}（统一账号中心）", ApplicationService.BUILTIN_CODE);
    }

    private String randomSecret() {
        String alphabet = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /**
     * 轻量 schema 迁移。
     *
     * <p>为什么不用 schema.sql 解决：MySQL 5.7 不支持 {@code ADD COLUMN IF NOT EXISTS}，
     * 而 {@code CREATE TABLE IF NOT EXISTS} 对已存在的表直接跳过。所以「给已有表加列 / 换唯一索引」
     * 只能在启动时查 information_schema 判断后执行，否则老库升级会静默停留在旧结构上。
     */
    private void migrateSchema() {
        try {
            ensurePermissionAppCode();
        } catch (Exception e) {
            log.error("权限表按应用分域迁移失败，按应用隔离权限的功能不可用：{}", e.getMessage(), e);
        }
    }

    /** sso_permission：补 app_code 列，并把全局唯一索引换成 (app_code, code) */
    private void ensurePermissionAppCode() {
        if (!tableExists("sso_permission")) {
            return;
        }
        Integer col = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'sso_permission' AND COLUMN_NAME = 'app_code'", Integer.class);
        if (col == null || col == 0) {
            jdbcTemplate.execute("ALTER TABLE sso_permission ADD COLUMN app_code VARCHAR(64) NOT NULL "
                    + "DEFAULT 'sso' COMMENT '所属应用编码；sso 表示平台自身'");
            log.info("迁移：sso_permission 已新增 app_code 列，历史权限全部归属 sso");
        }
        // 旧的单列唯一索引会让「不同应用使用同名权限码」互相冲突，必须先摘掉
        if (indexExists("sso_permission", "uk_perm_code")) {
            jdbcTemplate.execute("ALTER TABLE sso_permission DROP INDEX uk_perm_code");
            log.info("迁移：已移除 sso_permission 的全局唯一索引 uk_perm_code");
        }
        if (!indexExists("sso_permission", "uk_perm_app_code")) {
            jdbcTemplate.execute("ALTER TABLE sso_permission ADD UNIQUE KEY uk_perm_app_code (app_code, code)");
            log.info("迁移：已创建唯一索引 uk_perm_app_code(app_code, code)");
        }
        if (!indexExists("sso_permission", "idx_perm_app")) {
            jdbcTemplate.execute("ALTER TABLE sso_permission ADD KEY idx_perm_app (app_code, deleted)");
        }
    }

    private boolean tableExists(String table) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class, table);
        return n != null && n > 0;
    }

    private boolean indexExists(String table, String index) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = ? AND INDEX_NAME = ?", Integer.class, table, index);
        return n != null && n > 0;
    }

    /**
     * 初始化内置权限（菜单 + 按钮）。幂等 upsert：只补缺失的码，不动已存在行——
     * 这样后续在 {@code BUILTIN} 里新增权限码时，老库会自动补齐，无需重建。
     */
    private void initPermissions() {
        seedPermissions(PermissionConstants.PLATFORM_APP, PermissionConstants.BUILTIN);
        // 业务应用 ehr 的权限码（通知管理）同样幂等播种：sso_permission 按 app_code 隔离
        seedPermissions(PermissionConstants.EHR_APP, PermissionConstants.EHR_BUILTIN);
    }

    /** 按应用补权限码；父级菜单的 id 只在同应用内找，避免业务应用同名码把 parentId 指错 */
    private void seedPermissions(String appCode, List<PermDef> defs) {
        Map<String, Long> codeToId = new HashMap<>();
        for (SsoPermission existing : permissionMapper.selectList(null)) {
            if (appCode.equals(existing.getAppCode())) {
                codeToId.put(existing.getCode(), existing.getId());
            }
        }
        boolean added = false;
        for (PermDef def : defs) {
            if (codeToId.containsKey(def.code())) {
                continue; // 已存在，保留不动
            }
            SsoPermission p = new SsoPermission();
            p.setAppCode(appCode);
            p.setCode(def.code());
            p.setName(def.name());
            p.setType(def.type().getCode());
            p.setParentId(def.parentCode() == null ? 0L : codeToId.getOrDefault(def.parentCode(), 0L));
            p.setSort(def.sort());
            p.setRemark("");
            permissionMapper.insert(p);
            codeToId.put(def.code(), p.getId());
            added = true;
        }
        if (added) {
            log.info("补充缺失的内置权限完成：app={}", appCode);
        } else {
            log.info("内置权限已存在，跳过：app={}", appCode);
        }
    }

    /**
     * 通知相关的默认角色授权（幂等）：
     * <ul>
     *   <li>员工自助 {@code EMPLOYEE_SELF} → 通知中心菜单与查看权限；</li>
     *   <li>人力 / 运营相关角色 → 人力中心的通知管理与维护权限。</li>
     * </ul>
     * 角色或权限码不存在时静默跳过，不阻断启动；管理员仍可在「权限管理」里手工补授权。
     */
    private void initNoticeRoleBindings() {
        boolean changed = bindRolePermissions(PermissionConstants.NOTICE_ROLE_CODES,
                PermissionConstants.PLATFORM_APP,
                PermissionConstants.MENU_NOTICE, PermissionConstants.NOTICE_VIEW);
        changed |= bindRolePermissions(PermissionConstants.ANNOUNCEMENT_MANAGE_ROLE_CODES,
                PermissionConstants.EHR_APP,
                PermissionConstants.EHR_MENU_ANNOUNCEMENT, PermissionConstants.EHR_ANNOUNCEMENT_MANAGE);
        if (changed) {
            log.info("通知相关权限已按角色补齐");
        }
    }

    /** 给一组角色补权限链接；返回是否真的新增过链接 */
    private boolean bindRolePermissions(List<String> roleCodes, String appCode, String... permCodes) {
        boolean changed = false;
        for (String roleCode : roleCodes) {
            SsoRole role = roleMapper.selectByCode(roleCode);
            if (role == null) {
                continue;
            }
            for (String code : permCodes) {
                SsoPermission p = permissionMapper.selectByCode(appCode, code);
                if (p == null) {
                    continue;
                }
                long cnt = rolePermissionMapper.selectCount(
                        new LambdaQueryWrapper<SsoRolePermission>()
                                .eq(SsoRolePermission::getRoleId, role.getId())
                                .eq(SsoRolePermission::getPermissionId, p.getId()));
                if (cnt == 0) {
                    SsoRolePermission rp = new SsoRolePermission();
                    rp.setRoleId(role.getId());
                    rp.setPermissionId(p.getId());
                    rolePermissionMapper.insert(rp);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** 初始化 SUPER_ADMIN 角色与内置管理员账户，并让该角色拥有全部权限（幂等补链） */
    private void initSuperAdmin() {
        SsoRole role = roleMapper.selectByCode(AccountContextService.SUPER_ADMIN_ROLE);
        if (role == null) {
            role = new SsoRole();
            role.setCode(AccountContextService.SUPER_ADMIN_ROLE);
            role.setName("超级管理员");
            role.setRemark("内置，拥有全部权限");
            roleMapper.insert(role);
        }

        // 无论是否新建角色，都把「权限表里存在、但角色尚未链接」的权限补齐，
        // 保证新增内置权限后超管也能拿到（@Permit 旁路放行之外，菜单/按钮码也齐全）
        for (SsoPermission p : permissionMapper.selectList(null)) {
            long cnt = rolePermissionMapper.selectCount(
                    new LambdaQueryWrapper<SsoRolePermission>()
                            .eq(SsoRolePermission::getRoleId, role.getId())
                            .eq(SsoRolePermission::getPermissionId, p.getId()));
            if (cnt == 0) {
                SsoRolePermission rp = new SsoRolePermission();
                rp.setRoleId(role.getId());
                rp.setPermissionId(p.getId());
                rolePermissionMapper.insert(rp);
            }
        }

        // 口令由配置中心下发；缺失时宁可跳过建号，也不要写进一个空口令的管理员
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("未配置 sso.bootstrap.admin-password（应由 Nacos 的 sso-account.yaml 下发，"
                    + "或以环境变量 SSO_ADMIN_PASSWORD 提供），本次跳过内置管理员创建。"
                    + "角色与权限已初始化，补齐口令后重启即可建号。");
            return;
        }

        SsoAccount admin = accountMapper.selectByUsername(adminUsername);
        if (admin == null) {
            admin = new SsoAccount();
            admin.setUsername(adminUsername);
            admin.setNickname("超级管理员");
            admin.setEmail(adminEmail);
            admin.setPasswordHash(encoder.encode(adminPassword));
            admin.setStatus(1);
            admin.setSource("local");
            admin.setPwdResetRequired(0);
            admin.setLoginFailCount(0);
            accountMapper.insert(admin);
        }

        // 账户 -> 角色（防重复）
        long bound = accountRoleMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SsoAccountRole>()
                        .eq(SsoAccountRole::getAccountId, admin.getId())
                        .eq(SsoAccountRole::getRoleId, role.getId()));
        if (bound == 0) {
            SsoAccountRole ar = new SsoAccountRole();
            ar.setAccountId(admin.getId());
            ar.setRoleId(role.getId());
            accountRoleMapper.insert(ar);
        }
        log.info("超级管理员初始化完成：用户名={}", adminUsername);
    }
}
