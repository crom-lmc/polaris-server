package com.laimengchao.sso.ehr.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.ehr.constant.PermissionCodes;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 人员数据范围。范围由权限码驱动，不依赖 sso 角色表：
 * <ul>
 *   <li>{@code employee:data:all} → 全部</li>
 *   <li>{@code employee:data:org_tree} → 当前登录人档案所在组织及下级</li>
 *   <li>{@code employee:data:org} → 仅当前登录人档案所在组织</li>
 *   <li>都没有 → 只能看自己的档案（按 accountId 匹配）</li>
 * </ul>
 * 超管直接放行全部。没有任何档案的账号在组织级范围下退化为「仅本人」，
 * 避免因为拿不到根组织而看到全量或什么都看不到。
 */
@Service
@RequiredArgsConstructor
public class DataScopeService {

    public enum ScopeType {
        ALL, ORG_TREE, ORG, SELF
    }

    public record Scope(ScopeType type, Long orgId, Long accountId) {
    }

    private final EmployeeMapper employeeMapper;
    private final OrgNodeMapper orgMapper;

    /** 解析当前登录人的数据范围 */
    public Scope current() {
        SecurityUser user = SecurityUtils.currentUser().orElse(null);
        Long accountId = user == null ? null : user.getAccountId();
        if (user != null && user.isSuperAdmin()) {
            return new Scope(ScopeType.ALL, null, accountId);
        }
        if (hasPermission(user, PermissionCodes.EMPLOYEE_DATA_ALL)) {
            return new Scope(ScopeType.ALL, null, accountId);
        }
        Employee me = me(accountId);
        Long orgId = me == null ? null : me.getOrgId();
        if (orgId != null && hasPermission(user, PermissionCodes.EMPLOYEE_DATA_ORG_TREE)) {
            return new Scope(ScopeType.ORG_TREE, orgId, accountId);
        }
        if (orgId != null && hasPermission(user, PermissionCodes.EMPLOYEE_DATA_ORG)) {
            return new Scope(ScopeType.ORG, orgId, accountId);
        }
        return new Scope(ScopeType.SELF, null, accountId);
    }

    /** 把数据范围拼进人员查询条件；调用方后续再叠加的业务筛选会自然与范围取交集 */
    public void applyTo(LambdaQueryWrapper<Employee> wrapper) {
        Scope scope = current();
        switch (scope.type()) {
            case ALL -> {
            }
            case ORG_TREE -> {
                List<Long> ids = subtreeIds(scope.orgId());
                if (ids.isEmpty()) {
                    wrapper.eq(Employee::getOrgId, -1L);
                } else {
                    wrapper.in(Employee::getOrgId, ids);
                }
            }
            case ORG -> wrapper.eq(Employee::getOrgId, scope.orgId());
            case SELF -> wrapper.eq(Employee::getAccountId, scope.accountId());
        }
    }

    /** 单条档案是否在当前数据范围内（详情/子档案/导出用） */
    public boolean canAccess(Employee employee) {
        if (employee == null) {
            return false;
        }
        Scope scope = current();
        return switch (scope.type()) {
            case ALL -> true;
            case SELF -> scope.accountId() != null && scope.accountId().equals(employee.getAccountId());
            case ORG -> scope.orgId() != null && scope.orgId().equals(employee.getOrgId());
            case ORG_TREE -> employee.getOrgId() != null && subtreeIds(scope.orgId()).contains(employee.getOrgId());
        };
    }

    /** 是否有敏感字段明文权限（含超管） */
    public boolean canRevealSensitive() {
        SecurityUser user = SecurityUtils.currentUser().orElse(null);
        return user != null
                && (user.isSuperAdmin() || hasPermission(user, PermissionCodes.EMPLOYEE_SENSITIVE_VIEW));
    }

    public boolean hasPermission(String code) {
        return hasPermission(SecurityUtils.currentUser().orElse(null), code);
    }

    private boolean hasPermission(SecurityUser user, String code) {
        return user != null && user.getPermissions() != null && user.getPermissions().contains(code);
    }

    private Employee me(Long accountId) {
        if (accountId == null) {
            return null;
        }
        return employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getAccountId, accountId).last("limit 1"));
    }

    /** 根组织自身 + 整棵子树的 id（按 path 前缀） */
    private List<Long> subtreeIds(Long rootOrgId) {
        if (rootOrgId == null) {
            return List.of();
        }
        OrgNode root = orgMapper.selectById(rootOrgId);
        if (root == null || root.getPath() == null || root.getPath().isBlank()) {
            return List.of(rootOrgId);
        }
        List<OrgNode> all = orgMapper.selectList(new LambdaQueryWrapper<OrgNode>()
                .likeRight(OrgNode::getPath, root.getPath()));
        List<Long> ids = all.stream().map(OrgNode::getId).distinct().collect(Collectors.toList());
        return ids.isEmpty() ? List.of(rootOrgId) : ids;
    }
}
