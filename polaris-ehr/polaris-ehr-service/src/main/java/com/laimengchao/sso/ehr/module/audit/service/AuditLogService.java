package com.laimengchao.sso.ehr.module.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.audit.entity.AuditLog;
import com.laimengchao.sso.ehr.module.audit.mapper.AuditLogMapper;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.security.SecurityUser;
import com.laimengchao.sso.ehr.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 敏感操作审计：详情查看 / 明文查看 / 导出都记一条，
 * 记录操作人、目标、涉及字段、IP 与 UA，供 {@code audit:view} 角色追查。
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    public static final String ACTION_VIEW = "VIEW";
    public static final String ACTION_PLAIN = "PLAIN";
    public static final String ACTION_EXPORT = "EXPORT";

    public static final String TARGET_EMPLOYEE = "EMPLOYEE";

    private final AuditLogMapper auditLogMapper;
    private final EmployeeMapper employeeMapper;

    /** 记录一条审计；取不到登录主体（内部调用）时不记，避免产生无主记录 */
    public void record(String action, String targetType, Long targetId, String fields) {
        SecurityUser user = SecurityUtils.currentUser().orElse(null);
        if (user == null || user.getAccountId() == null) {
            return;
        }
        AuditLog row = new AuditLog();
        row.setOperatorId(user.getAccountId());
        row.setOperatorName(user.getUsername());
        row.setAction(action);
        row.setTargetType(targetType);
        row.setTargetId(targetId);
        row.setFields(truncate(fields == null ? "" : fields, 255));
        HttpServletRequest request = currentRequest();
        if (request != null) {
            row.setIp(truncate(clientIp(request), 64));
            row.setUa(truncate(request.getHeader("User-Agent"), 255));
        }
        auditLogMapper.insert(row);
    }

    public R.Page<AuditLog> page(String action, String targetType, Long targetId, String operator,
                                 LocalDate startDate, LocalDate endDate, int page, int size) {
        LambdaQueryWrapper<AuditLog> w = new LambdaQueryWrapper<>();
        if (action != null && !action.isBlank()) {
            w.eq(AuditLog::getAction, action.trim());
        }
        if (targetType != null && !targetType.isBlank()) {
            w.eq(AuditLog::getTargetType, targetType.trim());
        }
        if (targetId != null) {
            w.eq(AuditLog::getTargetId, targetId);
        }
        if (operator != null && !operator.isBlank()) {
            w.like(AuditLog::getOperatorName, operator.trim());
        }
        if (startDate != null) {
            w.ge(AuditLog::getCreatedAt, startDate.atStartOfDay());
        }
        if (endDate != null) {
            w.lt(AuditLog::getCreatedAt, endDate.plusDays(1).atStartOfDay());
        }
        w.orderByDesc(AuditLog::getCreatedAt).orderByDesc(AuditLog::getId);
        IPage<AuditLog> ip = auditLogMapper.selectPage(new Page<>(page, size), w);
        fillTargetNames(ip.getRecords());
        return R.Page.of(ip.getRecords(), ip.getTotal());
    }

    /** 员工目标补姓名，审计页直接可读；其它类型前端按 id 展示 */
    private void fillTargetNames(List<AuditLog> rows) {
        List<Long> employeeIds = rows.stream()
                .filter(r -> TARGET_EMPLOYEE.equals(r.getTargetType()) && r.getTargetId() != null)
                .map(AuditLog::getTargetId)
                .distinct()
                .collect(Collectors.toList());
        if (employeeIds.isEmpty()) {
            return;
        }
        Map<Long, String> names = employeeMapper.selectBatchIds(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName, (a, b) -> a));
        for (AuditLog row : rows) {
            if (TARGET_EMPLOYEE.equals(row.getTargetType()) && row.getTargetId() != null) {
                row.setTargetName(names.get(row.getTargetId()));
            }
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
