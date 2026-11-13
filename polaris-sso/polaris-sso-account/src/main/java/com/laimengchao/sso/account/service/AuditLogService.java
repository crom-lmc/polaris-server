package com.laimengchao.sso.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.account.domain.entity.SsoAuditLog;
import com.laimengchao.sso.account.mapper.SsoAuditLogMapper;
import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.common.security.AuditLogWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/** SSO 敏感操作审计；登录成功/失败仍由 LoginLogService 记录。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
    public static final String ROLE_CREATE = "ROLE_CREATE";
    public static final String ROLE_UPDATE = "ROLE_UPDATE";
    public static final String ROLE_DELETE = "ROLE_DELETE";
    public static final String ROLE_PERMISSION_ASSIGN = "ROLE_PERMISSION_ASSIGN";
    public static final String PERMISSION_CREATE = "PERMISSION_CREATE";
    public static final String PERMISSION_UPDATE = "PERMISSION_UPDATE";
    public static final String PERMISSION_DELETE = "PERMISSION_DELETE";
    public static final String PERMISSION_IMPORT = "PERMISSION_IMPORT";
    public static final String PERMISSION_DELETE_BY_APP = "PERMISSION_DELETE_BY_APP";

    public static final String TARGET_ACCOUNT = "ACCOUNT";
    public static final String TARGET_ROLE = "ROLE";
    public static final String TARGET_PERMISSION = "PERMISSION";
    public static final String TARGET_APPLICATION = "APPLICATION";

    private static final String TABLE = "sso_audit_log";

    private final JdbcTemplate jdbcTemplate;
    private final SsoAuditLogMapper auditLogMapper;

    public void record(String action, String targetType, Long targetId, String fields) {
        AuditLogWriter.record(jdbcTemplate, TABLE, action, targetType, targetId, fields);
    }

    /** 失败审计尽力写入，不能用审计异常覆盖原业务异常。 */
    public void recordFailure(String action, String targetType, Long targetId, String fields) {
        try {
            AuditLogWriter.record(jdbcTemplate, TABLE, action, targetType, targetId, false, fields);
        } catch (Exception e) {
            log.warn("写入失败操作审计失败（已忽略）: {}", e.getMessage());
        }
    }

    public void recordInternal(Long operatorId, String operatorName, String action,
                               String targetType, Long targetId, String fields,
                               String clientIp, String userAgent) {
        AuditLogWriter.record(jdbcTemplate, TABLE, operatorId, operatorName, action,
                targetType, targetId, fields, clientIp, userAgent);
    }

    public PageResult<SsoAuditLog> page(int page, int size, String action, String targetType,
                                        Long targetId, String operator, String result,
                                        LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<SsoAuditLog> w = new LambdaQueryWrapper<>();
        if (action != null && !action.isBlank()) {
            w.eq(SsoAuditLog::getAction, action.trim());
        }
        if (targetType != null && !targetType.isBlank()) {
            w.eq(SsoAuditLog::getTargetType, targetType.trim());
        }
        if (targetId != null) {
            w.eq(SsoAuditLog::getTargetId, targetId);
        }
        if (operator != null && !operator.isBlank()) {
            w.like(SsoAuditLog::getOperatorName, operator.trim());
        }
        if (result != null && !result.isBlank()) {
            w.likeRight(SsoAuditLog::getFields, "result=" + result.trim());
        }
        if (startDate != null) {
            w.ge(SsoAuditLog::getCreatedAt, startDate.atStartOfDay());
        }
        if (endDate != null) {
            w.lt(SsoAuditLog::getCreatedAt, endDate.plusDays(1).atStartOfDay());
        }
        w.orderByDesc(SsoAuditLog::getCreatedAt).orderByDesc(SsoAuditLog::getId);
        IPage<SsoAuditLog> resultPage = auditLogMapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(resultPage.getRecords(), resultPage.getTotal(), page, size);
    }
}
