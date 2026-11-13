package com.laimengchao.sso.common.security;

import com.laimengchao.sso.utils.AccountContext;
import com.laimengchao.sso.utils.IdUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 通用敏感操作审计写入器。各服务写自己的审计表，共用操作人、请求上下文与字段约束。
 * 表结构需与 ehr_audit_log 一致；fields 以 result=success/fail 开头，后接非敏感详情。
 */
public final class AuditLogWriter {

    private static final Pattern TABLE_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private AuditLogWriter() {
    }

    public static void record(JdbcTemplate jdbcTemplate, String tableName, String action,
                              String targetType, Long targetId, String fields) {
        record(jdbcTemplate, tableName, action, targetType, targetId, true, fields);
    }

    public static void record(JdbcTemplate jdbcTemplate, String tableName, String action,
                              String targetType, Long targetId, boolean success, String fields) {
        Long operatorId = AccountContext.getAccountId();
        String operatorName = AccountContext.getUsername();
        if (operatorId == null) {
            SecurityUser user = SecurityUtils.currentUser().orElse(null);
            if (user != null) {
                operatorId = user.getAccountId();
                operatorName = user.getUsername();
            }
        }
        // 内部任务没有登录主体时必须由调用方显式指定操作人，避免产生无主审计。
        if (operatorId == null) {
            return;
        }
        HttpServletRequest request = currentRequest();
        String ip = request == null ? "" : clientIp(request);
        String ua = request == null ? "" : request.getHeader("User-Agent");
        write(jdbcTemplate, tableName, operatorId, operatorName, action, targetType, targetId,
                success, fields, ip, ua);
    }

    /** 服务间调用没有登录上下文时，由调用方显式提供操作人及原始客户端信息。 */
    public static void record(JdbcTemplate jdbcTemplate, String tableName,
                              Long operatorId, String operatorName, String action,
                              String targetType, Long targetId, String fields,
                              String clientIp, String userAgent) {
        if (operatorId == null) {
            return;
        }
        write(jdbcTemplate, tableName, operatorId, operatorName, action, targetType, targetId,
                true, fields, clientIp, userAgent);
    }

    private static void write(JdbcTemplate jdbcTemplate, String tableName,
                              Long operatorId, String operatorName, String action,
                              String targetType, Long targetId, boolean success, String fields,
                              String clientIp, String userAgent) {
        if (!TABLE_NAME.matcher(tableName).matches()) {
            throw new IllegalArgumentException("非法审计表名");
        }
        String detail = "result=" + (success ? "success" : "fail");
        if (fields != null && !fields.isBlank()) {
            detail += ";" + fields.trim();
        }
        jdbcTemplate.update("INSERT INTO " + tableName + " "
                        + "(id, operator_id, operator_name, action, target_type, target_id, fields, ip, ua, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                IdUtils.nextId(), operatorId, truncate(operatorName, 64), truncate(action, 32),
                truncate(targetType, 32), targetId, truncate(detail, 255),
                truncate(clientIp, 64), truncate(userAgent, 255), LocalDateTime.now());
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
