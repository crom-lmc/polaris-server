package com.laimengchao.sso.settings.module.security.service;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.settings.module.security.dto.SecurityConfigRequest;
import com.laimengchao.sso.settings.module.security.entity.SecurityConfig;
import com.laimengchao.sso.settings.module.security.mapper.SecurityConfigMapper;
import com.laimengchao.sso.common.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 安全配置：单行配置（id 固定 1），首次读取时按 DB 默认值补齐。
 */
@Slf4j
@Service
public class SecurityConfigService {

    private static final long CONFIG_ID = 1L;
    /** 合法密码字符类型 */
    private static final Set<String> RULE_CODES = Set.of("DIGIT", "UPPER", "LOWER", "SPECIAL");
    private static final Set<String> PASSWORD_MODES = Set.of("RANDOM", "FIXED");
    private static final Set<String> LOCK_MODES = Set.of("ACCOUNT", "EMAIL");

    private final SecurityConfigMapper mapper;

    public SecurityConfigService(SecurityConfigMapper mapper) {
        this.mapper = mapper;
    }

    /** 读取配置；首行缺失时插入空行，其余字段由 DDL 的 DEFAULT 提供 */
    @Transactional
    public SecurityConfig get() {
        SecurityConfig config = mapper.selectById(CONFIG_ID);
        if (config == null) {
            SecurityConfig row = new SecurityConfig();
            row.setId(CONFIG_ID);
            mapper.insert(row);
            config = mapper.selectById(CONFIG_ID);
        }
        return config;
    }

    /** 保存并发回最新配置 */
    @Transactional
    public SecurityConfig save(SecurityConfigRequest req) {
        String rules = normalizeRules(req.getPasswordRules());
        validate(req, rules);

        SecurityConfig config = get();
        config.setInitialPasswordMode(req.getInitialPasswordMode());
        config.setDefaultPassword(req.getDefaultPassword() == null ? "" : req.getDefaultPassword().trim());
        config.setPasswordRules(rules);
        config.setPasswordMinLength(req.getPasswordMinLength());
        config.setPasswordMaxLength(req.getPasswordMaxLength());
        config.setPasswordLockMode(req.getPasswordLockMode());
        config.setLoginFailLimit(req.getLoginFailLimit());
        config.setLoginFailLockMinutes(req.getLoginFailLockMinutes());
        config.setLoginFailDetectCount(req.getLoginFailDetectCount());
        config.setForceChangePassword(req.getForceChangePassword());
        config.setSessionTimeoutMinutes(req.getSessionTimeoutMinutes());
        config.setMaxLoginErrorCount(req.getMaxLoginErrorCount());
        config.setUserLockMinutes(req.getUserLockMinutes());
        config.setEmailCodeDigits(req.getEmailCodeDigits());
        config.setVerifyCodeExpireMinutes(req.getVerifyCodeExpireMinutes());
        config.setUpdatedBy(SecurityUtils.currentUsername().orElse(null));
        mapper.updateById(config);
        return mapper.selectById(CONFIG_ID);
    }

    /** 规则去重 + 按固定顺序拼接，避免前端传参顺序不同造成前后对比差异 */
    private String normalizeRules(List<String> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "密码规则至少选择一项");
        }
        List<String> ordered = Arrays.stream(new String[]{"DIGIT", "UPPER", "LOWER", "SPECIAL"})
                .filter(rules::contains)
                .toList();
        for (String r : rules) {
            if (!RULE_CODES.contains(r)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "未知的密码规则：" + r);
            }
        }
        return String.join(",", ordered);
    }

    private void validate(SecurityConfigRequest req, String rules) {
        if (!PASSWORD_MODES.contains(req.getInitialPasswordMode())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "初始密码规则只能是 RANDOM 或 FIXED");
        }
        if ("FIXED".equals(req.getInitialPasswordMode())
                && (req.getDefaultPassword() == null || req.getDefaultPassword().isBlank())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "固定密码模式下默认密码不能为空");
        }
        if (!LOCK_MODES.contains(req.getPasswordLockMode())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "违约密码锁只能是 ACCOUNT 或 EMAIL");
        }
        if (req.getPasswordMaxLength() < req.getPasswordMinLength()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "密码最大长度不能小于最小长度");
        }
        if (rules.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "密码规则至少选择一项");
        }
    }
}
