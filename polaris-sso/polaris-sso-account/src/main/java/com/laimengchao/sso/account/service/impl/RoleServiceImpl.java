package com.laimengchao.sso.account.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.account.domain.entity.SsoAccountRole;
import com.laimengchao.sso.account.domain.entity.SsoPermission;
import com.laimengchao.sso.account.domain.entity.SsoRole;
import com.laimengchao.sso.account.domain.entity.SsoRolePermission;
import com.laimengchao.sso.account.dto.request.CreateRoleRequest;
import com.laimengchao.sso.account.dto.request.UpdateRoleRequest;
import com.laimengchao.sso.account.dto.response.RoleVO;
import com.laimengchao.sso.account.mapper.SsoAccountRoleMapper;
import com.laimengchao.sso.account.mapper.SsoPermissionMapper;
import com.laimengchao.sso.account.mapper.SsoRoleMapper;
import com.laimengchao.sso.account.mapper.SsoRolePermissionMapper;
import com.laimengchao.sso.account.security.AccountContextService;
import com.laimengchao.sso.account.service.RoleService;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.utils.Asserts;
import com.laimengchao.sso.utils.IdUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final SsoRoleMapper roleMapper;
    private final SsoRolePermissionMapper rolePermissionMapper;
    private final SsoAccountRoleMapper accountRoleMapper;
    private final SsoPermissionMapper permissionMapper;

    /** 平台角色域：权限码里的 app_code=sso，不对应 sso_application 里的记录 */
    public static final String PLATFORM_APP = "sso";

    @Override
    public PageResult<RoleVO> page(int page, int size, String keyword, String appCode) {
        LambdaQueryWrapper<SsoRole> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(SsoRole::getName, keyword).or().like(SsoRole::getCode, keyword));
        }
        if (appCode != null && !appCode.isBlank()) {
            w.eq(SsoRole::getAppCode, appCode.trim());
        }
        w.orderByAsc(SsoRole::getAppCode).orderByAsc(SsoRole::getCode);
        IPage<SsoRole> ip = roleMapper.selectPage(new Page<>(page, size), w);
        List<RoleVO> records = ip.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(records, ip.getTotal(), page, size);
    }

    @Override
    public List<RoleVO> list(String appCode) {
        LambdaQueryWrapper<SsoRole> w = new LambdaQueryWrapper<>();
        if (appCode != null && !appCode.isBlank()) {
            w.eq(SsoRole::getAppCode, appCode.trim());
        }
        w.orderByAsc(SsoRole::getAppCode).orderByAsc(SsoRole::getCode);
        return roleMapper.selectList(w).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public RoleVO detail(Long id) {
        SsoRole role = roleMapper.selectById(id);
        Asserts.notNull(role, ErrorCode.NOT_FOUND, "角色不存在");
        return toVO(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleVO create(CreateRoleRequest req) {
        Asserts.notEmpty(req.getCode(), ErrorCode.PARAM_ERROR, "角色编码必填");
        Asserts.notEmpty(req.getName(), ErrorCode.PARAM_ERROR, "角色名称必填");
        String code = req.getCode().trim();
        Asserts.isTrue(code.matches("^[A-Za-z][A-Za-z0-9_]{1,63}$"), ErrorCode.PARAM_ERROR,
                "角色编码只能是字母开头的字母/数字/下划线组合，2-64 位");
        Set<Long> permissionIds = validPermissionIds(req.getPermissionIds());
        // 角色是全局对象：不再按应用限制可授权的权限范围，统一落在平台域
        String appCode = PLATFORM_APP;

        // 唯一索引 uk_role_code 不含 deleted 列，被逻辑删除过的同码角色会挡住新插入，改为复活复用
        SsoRole existing = roleMapper.selectByCodeIncludingDeleted(code);
        if (existing != null) {
            Asserts.isTrue(Integer.valueOf(1).equals(existing.getDeleted()), ErrorCode.CONFLICT,
                    "角色编码已存在：" + code);
            roleMapper.restore(existing.getId());
            existing.setDeleted(0);
            existing.setAppCode(appCode);
            existing.setName(req.getName());
            existing.setRemark(req.getRemark() == null ? "" : req.getRemark());
            roleMapper.updateById(existing);
            replacePermissions(existing.getId(), permissionIds);
            return toVO(existing);
        }

        SsoRole role = new SsoRole();
        role.setId(IdUtils.nextId());
        role.setCode(code);
        role.setAppCode(appCode);
        role.setName(req.getName());
        role.setRemark(req.getRemark() == null ? "" : req.getRemark());
        roleMapper.insert(role);
        replacePermissions(role.getId(), permissionIds);
        return toVO(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleVO update(Long id, UpdateRoleRequest req) {
        SsoRole role = roleMapper.selectById(id);
        Asserts.notNull(role, ErrorCode.NOT_FOUND, "角色不存在");
        if (req.getName() != null) {
            Asserts.notEmpty(req.getName(), ErrorCode.PARAM_ERROR, "角色名称不能为空");
            role.setName(req.getName());
        }
        if (req.getRemark() != null) {
            role.setRemark(req.getRemark());
        }
        roleMapper.updateById(role);

        // permissionIds 为 null 表示「本次不改权限」，与空数组（收回全部）区分开
        if (req.getPermissionIds() != null) {
            replacePermissions(id, validPermissionIds(req.getPermissionIds()));
        }
        return toVO(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SsoRole role = roleMapper.selectById(id);
        Asserts.notNull(role, ErrorCode.NOT_FOUND, "角色不存在");
        Asserts.isFalse(AccountContextService.SUPER_ADMIN_ROLE.equals(role.getCode()),
                ErrorCode.FORBIDDEN, "内置超级管理员角色不可删除");

        Long used = accountRoleMapper.selectCount(
                new LambdaQueryWrapper<SsoAccountRole>().eq(SsoAccountRole::getRoleId, id));
        Asserts.isTrue(used == null || used == 0L, ErrorCode.CONFLICT,
                "该角色下还有 " + used + " 个账户，请先把这些账户改到别的角色");

        roleMapper.deleteById(id);
        rolePermissionMapper.delete(new LambdaQueryWrapper<SsoRolePermission>()
                .eq(SsoRolePermission::getRoleId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long id, List<Long> permissionIds) {
        SsoRole role = roleMapper.selectById(id);
        Asserts.notNull(role, ErrorCode.NOT_FOUND, "角色不存在");
        Set<Long> ids = validPermissionIds(permissionIds);
        // 角色可以跨应用授权：人事专员这类角色能同时挂 EHR、CMS 等应用的权限点
        replacePermissions(id, ids);
    }

    // ---------------- 内部方法 ----------------

    /** 覆盖式写权限关联 */
    private void replacePermissions(Long roleId, Set<Long> permissionIds) {
        rolePermissionMapper.delete(new LambdaQueryWrapper<SsoRolePermission>()
                .eq(SsoRolePermission::getRoleId, roleId));
        for (Long pid : permissionIds) {
            SsoRolePermission rp = new SsoRolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(pid);
            rolePermissionMapper.insert(rp);
        }
    }

    /**
     * 去重并校验权限真实存在。
     * 不做校验的话，前端传个过期 id 也能写进关联表，之后这个角色永远少一个权限且查不出原因。
     */
    private Set<Long> validPermissionIds(List<Long> raw) {
        Set<Long> ids = new LinkedHashSet<>();
        if (raw == null) {
            return ids;
        }
        for (Long pid : raw) {
            if (pid != null) {
                ids.add(pid);
            }
        }
        if (ids.isEmpty()) {
            return ids;
        }
        List<SsoPermission> found = permissionMapper.selectBatchIds(ids);
        Asserts.isTrue(found.size() == ids.size(), ErrorCode.PARAM_ERROR,
                "存在无效的权限ID，请刷新页面后重试");
        return ids;
    }

    private RoleVO toVO(SsoRole role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setCode(role.getCode());
        vo.setAppCode(role.getAppCode() == null ? PLATFORM_APP : role.getAppCode());
        vo.setName(role.getName());
        vo.setRemark(role.getRemark());
        vo.setCreatedAt(role.getCreatedAt());
        vo.setBuiltin(AccountContextService.SUPER_ADMIN_ROLE.equals(role.getCode()));
        List<Long> permIds = rolePermissionMapper.selectList(
                        new LambdaQueryWrapper<SsoRolePermission>().eq(SsoRolePermission::getRoleId, role.getId()))
                .stream().map(SsoRolePermission::getPermissionId).collect(Collectors.toList());
        vo.setPermissionIds(permIds);

        Long accountCount = accountRoleMapper.selectCount(
                new LambdaQueryWrapper<SsoAccountRole>().eq(SsoAccountRole::getRoleId, role.getId()));
        vo.setAccountCount(accountCount == null ? 0L : accountCount);
        return vo;
    }
}
