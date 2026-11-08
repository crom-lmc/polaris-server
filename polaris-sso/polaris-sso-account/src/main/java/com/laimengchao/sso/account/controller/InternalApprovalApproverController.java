package com.laimengchao.sso.account.controller;

import com.laimengchao.sso.common.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 审批人解析用的账号反查接口。
 * <p>
 * 审批模板里可以把「角色」或「权限点」配成审批人，settings 需要把它们展开成具体的人。
 * 这类查询天然属于账号中心（它才知道谁有什么角色/权限），所以放这里而不是让 settings 直连 sso_ms 库。
 * <p>
 * 只返回正常状态（status=1）的账号：停用/锁定的人不该继续接审批单。
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalApprovalApproverController {

    private final JdbcTemplate jdbc;

    /**
     * 全量角色清单（<b>不按应用过滤</b>），审批模板「角色」审批人规则的下拉数据源。
     * 角色归属应用只是管理维度的分组，审批人配置关心的是「谁能审」，
     * 所以这里把所有应用的角色一次性吐出去，应用归属交给前端展示。
     */
    @GetMapping("/roles")
    public R<List<Map<String, Object>>> roles() {
        try {
            return R.ok(jdbc.queryForList("""
                    SELECT CAST(id AS CHAR) AS id, code, name, app_code AS appCode
                      FROM sso_role
                     WHERE deleted = 0
                     ORDER BY app_code, code
                    """));
        } catch (Exception e) {
            log.warn("角色清单查询失败：{}", e.getMessage());
            return R.ok(List.of());
        }
    }

    /** 某角色下的所有账号 */
    @GetMapping("/roles/{code}/accounts")
    public R<List<Map<String, Object>>> roleAccounts(@PathVariable String code) {
        return R.ok(query("""
                SELECT DISTINCT a.id AS id,
                       COALESCE(NULLIF(a.nickname, ''), a.username) AS name
                  FROM sso_account a
                  JOIN sso_account_role ar ON ar.account_id = a.id
                  JOIN sso_role r ON r.id = ar.role_id AND r.deleted = 0
                 WHERE r.code = ? AND a.deleted = 0 AND a.status = 1
                """, code));
    }

    /** 持有某权限点的所有账号（权限 → 角色 → 账号） */
    @GetMapping("/permissions/{code}/accounts")
    public R<List<Map<String, Object>>> permissionAccounts(@PathVariable String code) {
        return R.ok(query("""
                SELECT DISTINCT a.id AS id,
                       COALESCE(NULLIF(a.nickname, ''), a.username) AS name
                  FROM sso_account a
                  JOIN sso_account_role ar ON ar.account_id = a.id
                  JOIN sso_role_permission rp ON rp.role_id = ar.role_id
                  JOIN sso_permission p ON p.id = rp.permission_id AND p.deleted = 0
                 WHERE p.code = ? AND a.deleted = 0 AND a.status = 1
                """, code));
    }

    private List<Map<String, Object>> query(String sql, String code) {
        try {
            return jdbc.queryForList(sql, code);
        } catch (Exception e) {
            log.warn("审批人反查失败：code={} err={}", code, e.getMessage());
            return List.of();
        }
    }
}
