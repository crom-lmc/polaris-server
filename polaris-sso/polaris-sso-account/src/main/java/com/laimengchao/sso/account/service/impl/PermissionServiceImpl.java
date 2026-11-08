package com.laimengchao.sso.account.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.account.constant.PermissionConstants;
import com.laimengchao.sso.account.domain.entity.SsoPermission;
import com.laimengchao.sso.account.domain.entity.SsoRole;
import com.laimengchao.sso.account.domain.entity.SsoRolePermission;
import com.laimengchao.sso.account.dto.request.CreatePermissionRequest;
import com.laimengchao.sso.account.dto.request.UpdatePermissionRequest;
import com.laimengchao.sso.account.dto.response.PermissionVO;
import com.laimengchao.sso.account.mapper.SsoApplicationMapper;
import com.laimengchao.sso.account.mapper.SsoPermissionMapper;
import com.laimengchao.sso.account.mapper.SsoRoleMapper;
import com.laimengchao.sso.account.mapper.SsoRolePermissionMapper;
import com.laimengchao.sso.account.security.AccountContextService;
import com.laimengchao.sso.account.service.PermissionService;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.enums.PermissionType;
import com.laimengchao.sso.utils.Asserts;
import com.laimengchao.sso.utils.IdUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    /**
     * 权限码格式：小写字母开头，允许小写字母/数字/冒号/下划线/中划线，总长 2-64。
     * 冒号约定为「资源:操作」的分隔符，前端按同一规则拼码。
     */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9:_-]{1,63}$");

    /** 应用编码格式：与 sso_application.code 一致（小写字母开头，字母数字中划线） */
    private static final Pattern APP_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,63}$");

    private final SsoPermissionMapper permissionMapper;
    private final SsoRolePermissionMapper rolePermissionMapper;
    private final SsoRoleMapper roleMapper;
    private final SsoApplicationMapper applicationMapper;

    @Override
    public List<PermissionVO> tree(String appCode) {
        List<SsoPermission> all = load(appCode);
        Map<Long, PermissionVO> map = new LinkedHashMap<>();
        for (SsoPermission p : all) {
            map.put(p.getId(), toVO(p));
        }
        List<PermissionVO> roots = new ArrayList<>();
        for (SsoPermission p : all) {
            PermissionVO vo = map.get(p.getId());
            Long parentId = p.getParentId() == null ? 0L : p.getParentId();
            if (parentId == 0L) {
                roots.add(vo);
                continue;
            }
            PermissionVO parent = map.get(parentId);
            // 父节点必须同属一个应用：跨应用挂靠说明数据被绕过接口写坏了，宁可让它成为根也不能串树
            if (parent == null || !equalsApp(parent.getAppCode(), p.getAppCode())) {
                roots.add(vo);
                continue;
            }
            if (parent.getChildren() == null) {
                parent.setChildren(new ArrayList<>());
            }
            parent.getChildren().add(vo);
        }
        return roots;
    }

    @Override
    public List<PermissionVO> listAll(String appCode) {
        List<PermissionVO> list = new ArrayList<>();
        for (SsoPermission p : load(appCode)) {
            list.add(toVO(p));
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PermissionVO create(CreatePermissionRequest req) {
        String appCode = resolveApp(req.getAppCode());
        String code = trim(req.getCode());
        Asserts.notEmpty(code, ErrorCode.PARAM_ERROR, "权限码必填");
        Asserts.notEmpty(req.getName(), ErrorCode.PARAM_ERROR, "权限名称必填");
        Asserts.isTrue(CODE_PATTERN.matcher(code).matches(), ErrorCode.PARAM_ERROR,
                "权限码只能是小写字母开头，含小写字母/数字/冒号/下划线/中划线，2-64 位");

        int type = req.getType() == null ? PermissionType.BUTTON.getCode() : req.getType();
        Asserts.isTrue(type == PermissionType.MENU.getCode() || type == PermissionType.BUTTON.getCode(),
                ErrorCode.PARAM_ERROR, "权限类型只能是 1(菜单) 或 2(按钮)");
        Long parentId = resolveParentId(appCode, req);
        checkParent(parentId, appCode);

        return toVO(createOrRestore(appCode, code, req, type, parentId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PermissionVO update(Long id, UpdatePermissionRequest req) {
        SsoPermission p = permissionMapper.selectById(id);
        Asserts.notNull(p, ErrorCode.NOT_FOUND, "权限不存在");

        // 改码：内置码被 @Permit 硬引用，改了接口就失联，所以只允许改非内置的
        if (req.getCode() != null && !req.getCode().isBlank()) {
            String code = trim(req.getCode());
            if (!code.equals(p.getCode())) {
                Asserts.isFalse(PermissionConstants.isBuiltin(p.getAppCode(), p.getCode()), ErrorCode.FORBIDDEN,
                        "内置权限码不可修改（被接口注解直接引用）");
                Asserts.isTrue(CODE_PATTERN.matcher(code).matches(), ErrorCode.PARAM_ERROR,
                        "权限码只能是小写字母开头，含小写字母/数字/冒号/下划线/中划线，2-64 位");
                SsoPermission dup = permissionMapper.selectByCode(p.getAppCode(), code);
                Asserts.isTrue(dup == null, ErrorCode.CONFLICT, "权限码已存在：" + code);
                p.setCode(code);
            }
        }

        int type = req.getType() == null ? (p.getType() == null ? PermissionType.BUTTON.getCode() : p.getType())
                : req.getType();
        if (req.getType() != null) {
            Asserts.isTrue(type == PermissionType.MENU.getCode() || type == PermissionType.BUTTON.getCode(),
                    ErrorCode.PARAM_ERROR, "权限类型只能是 1(菜单) 或 2(按钮)");
            // 菜单降级成按钮前必须没有子节点，否则子节点会变成孤儿
            if (type == PermissionType.BUTTON.getCode() && p.getType() != null
                    && p.getType() == PermissionType.MENU.getCode()) {
                Asserts.isTrue(childrenOf(id).isEmpty(), ErrorCode.CONFLICT,
                        "该菜单下还有子权限，不能改为按钮类型");
            }
        }

        Long parentId = req.getParentId() == null ? p.getParentId() : req.getParentId();
        if (parentId != null && parentId != 0L) {
            Asserts.isFalse(parentId.equals(id), ErrorCode.PARAM_ERROR, "上级不能是自己");
            checkParent(parentId, p.getAppCode());
            // 防环：上级不能是自己的后代
            Asserts.isFalse(isDescendant(id, parentId), ErrorCode.PARAM_ERROR, "上级不能是自己的子权限");
        }

        applyFields(p, req.getName(), type, parentId, req.getSort(), req.getRemark());
        permissionMapper.updateById(p);
        return toVO(p);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SsoPermission p = permissionMapper.selectById(id);
        Asserts.notNull(p, ErrorCode.NOT_FOUND, "权限不存在");
        Asserts.isFalse(PermissionConstants.isBuiltin(p.getAppCode(), p.getCode()), ErrorCode.FORBIDDEN,
                "内置权限不可删除（被接口注解直接引用，删掉接口会永久 403）");
        Asserts.isTrue(childrenOf(id).isEmpty(), ErrorCode.CONFLICT,
                "该菜单下还有子权限，请先删除子权限");
        Long used = rolePermissionMapper.selectCount(
                new LambdaQueryWrapper<SsoRolePermission>().eq(SsoRolePermission::getPermissionId, id));
        Asserts.isTrue(used == null || used == 0L, ErrorCode.CONFLICT,
                "该权限已被 " + used + " 个角色引用，请先在角色里取消授权");

        rolePermissionMapper.delete(new LambdaQueryWrapper<SsoRolePermission>()
                .eq(SsoRolePermission::getPermissionId, id));
        permissionMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importForApp(String appCode, List<CreatePermissionRequest> items) {
        String app = resolveApp(appCode);
        Asserts.isTrue(items != null && !items.isEmpty(), ErrorCode.PARAM_ERROR, "权限清单不能为空");
        Asserts.isTrue(items.size() <= 500, ErrorCode.PARAM_ERROR, "单次最多导入 500 条");

        // 接入方手里只有自己定义的码，没有 SSO 的 ID，所以按 parentCode 解析。
        // 允许多轮：菜单可能晚于按钮出现在清单里，最多回溯 3 轮后仍解析不出就报错。
        List<CreatePermissionRequest> pending = new ArrayList<>(items);
        int created = 0;
        for (int round = 0; round < 3 && !pending.isEmpty(); round++) {
            boolean progress = false;
            Iterator<CreatePermissionRequest> it = pending.iterator();
            while (it.hasNext()) {
                CreatePermissionRequest item = it.next();
                String parentCode = trim(item.getParentCode());
                if (parentCode != null && !parentCode.isEmpty()
                        && permissionMapper.selectByCode(app, parentCode) == null) {
                    continue; // 上级还没建好，下一轮再来
                }
                if (importOne(app, item)) {
                    created++;
                }
                it.remove();
                progress = true;
            }
            if (!progress) {
                break;
            }
        }
        Asserts.isTrue(pending.isEmpty(), ErrorCode.PARAM_ERROR,
                "以下权限点的上级码无法解析：" + pending.stream().map(CreatePermissionRequest::getCode).toList());
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByApp(String appCode) {
        List<SsoPermission> list = permissionMapper.selectByApp(appCode);
        if (list.isEmpty()) {
            return 0;
        }
        List<Long> ids = list.stream().map(SsoPermission::getId).toList();
        rolePermissionMapper.delete(new LambdaQueryWrapper<SsoRolePermission>()
                .in(SsoRolePermission::getPermissionId, ids));
        permissionMapper.delete(new LambdaQueryWrapper<SsoPermission>()
                .eq(SsoPermission::getAppCode, appCode));
        return ids.size();
    }

    // ---------------- 内部方法 ----------------

    private List<SsoPermission> load(String appCode) {
        String app = trim(appCode);
        if (app == null || app.isEmpty()) {
            return permissionMapper.selectList(new LambdaQueryWrapper<SsoPermission>()
                    .orderByAsc(SsoPermission::getAppCode)
                    .orderByDesc(SsoPermission::getType)
                    .orderByAsc(SsoPermission::getSort)
                    .orderByAsc(SsoPermission::getId));
        }
        return permissionMapper.selectByApp(app);
    }

    /** 归一化并校验应用编码：空 -> sso；其余必须已在应用注册表里存在 */
    private String resolveApp(String appCode) {
        String code = trim(appCode);
        if (code == null || code.isEmpty()) {
            return PermissionConstants.PLATFORM_APP;
        }
        Asserts.isTrue(APP_PATTERN.matcher(code).matches(), ErrorCode.PARAM_ERROR,
                "应用编码只能是小写字母开头，含小写字母/数字/中划线，2-64 位");
        if (!PermissionConstants.PLATFORM_APP.equals(code)) {
            Asserts.notNull(applicationMapper.selectByCode(code), ErrorCode.NOT_FOUND,
                    "应用不存在，请先在「应用管理」注册：" + code);
        }
        return code;
    }

    /** parentId 优先；没有则用 parentCode 在同应用内反查 */
    private Long resolveParentId(String appCode, CreatePermissionRequest req) {
        if (req.getParentId() != null && req.getParentId() != 0L) {
            return req.getParentId();
        }
        String parentCode = trim(req.getParentCode());
        if (parentCode == null || parentCode.isEmpty()) {
            return 0L;
        }
        SsoPermission parent = permissionMapper.selectByCode(appCode, parentCode);
        Asserts.notNull(parent, ErrorCode.PARAM_ERROR, "上级权限码不存在：" + parentCode);
        return parent.getId();
    }

    private SsoPermission createOrRestore(String appCode, String code, CreatePermissionRequest req,
                                          int type, Long parentId) {
        // 唯一索引不含 deleted 列，被逻辑删除过的同码会挡住新插入，这里直接复活复用
        SsoPermission existing = permissionMapper.selectByCodeIncludingDeleted(appCode, code);
        if (existing != null) {
            Asserts.isTrue(Integer.valueOf(1).equals(existing.getDeleted()), ErrorCode.CONFLICT,
                    "权限码已存在：" + code);
            permissionMapper.restore(existing.getId());
            existing.setDeleted(0);
            existing.setAppCode(appCode);
            applyFields(existing, req.getName(), type, parentId, req.getSort(), req.getRemark());
            permissionMapper.updateById(existing);
            grantSuperAdmin(existing.getId());
            return existing;
        }

        SsoPermission p = new SsoPermission();
        p.setId(IdUtils.nextId());
        p.setAppCode(appCode);
        p.setCode(code);
        applyFields(p, req.getName(), type, parentId, req.getSort(), req.getRemark());
        permissionMapper.insert(p);
        grantSuperAdmin(p.getId());
        return p;
    }

    /**
     * 超级管理员默认拥有全部权限：新增/恢复权限后自动补链，
     * 这样新增菜单或按钮无需再去「角色管理」里手工授权。
     */
    private void grantSuperAdmin(Long permissionId) {
        SsoRole superAdmin = roleMapper.selectByCode(AccountContextService.SUPER_ADMIN_ROLE);
        if (superAdmin == null) {
            return;
        }
        Long exists = rolePermissionMapper.selectCount(new LambdaQueryWrapper<SsoRolePermission>()
                .eq(SsoRolePermission::getRoleId, superAdmin.getId())
                .eq(SsoRolePermission::getPermissionId, permissionId));
        if (exists != null && exists > 0) {
            return;
        }
        SsoRolePermission link = new SsoRolePermission();
        link.setRoleId(superAdmin.getId());
        link.setPermissionId(permissionId);
        rolePermissionMapper.insert(link);
    }

    /** 导入用：已存在则只刷新名称/排序/上级，返回是否新建 */
    private boolean importOne(String appCode, CreatePermissionRequest item) {
        String code = trim(item.getCode());
        Asserts.notEmpty(code, ErrorCode.PARAM_ERROR, "权限码必填");
        Asserts.isTrue(CODE_PATTERN.matcher(code).matches(), ErrorCode.PARAM_ERROR,
                "权限码格式不合法：" + code);
        int type = item.getType() == null ? PermissionType.BUTTON.getCode() : item.getType();
        Asserts.isTrue(type == PermissionType.MENU.getCode() || type == PermissionType.BUTTON.getCode(),
                ErrorCode.PARAM_ERROR, "权限类型只能是 1(菜单) 或 2(按钮)：" + code);
        Long parentId = resolveParentId(appCode, item);

        SsoPermission cur = permissionMapper.selectByCode(appCode, code);
        if (cur != null) {
            applyFields(cur, item.getName(), type, parentId, item.getSort(), item.getRemark());
            permissionMapper.updateById(cur);
            return false;
        }
        createOrRestore(appCode, code, item, type, parentId);
        return true;
    }

    private void applyFields(SsoPermission p, String name, int type, Long parentId,
                             Integer sort, String remark) {
        if (name != null) {
            p.setName(name);
        } else if (p.getName() == null) {
            p.setName("");
        }
        p.setType(type);
        p.setParentId(parentId == null ? 0L : parentId);
        p.setSort(sort == null ? (p.getSort() == null ? 0 : p.getSort()) : sort);
        p.setRemark(remark == null ? (p.getRemark() == null ? "" : remark) : remark);
    }

    /** 上级必须是同一应用的菜单节点——跨应用挂靠会让权限树串味 */
    private void checkParent(Long parentId, String appCode) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        SsoPermission parent = permissionMapper.selectById(parentId);
        Asserts.notNull(parent, ErrorCode.PARAM_ERROR, "上级权限不存在");
        Asserts.isTrue(parent.getType() != null && parent.getType() == PermissionType.MENU.getCode(),
                ErrorCode.PARAM_ERROR, "上级权限必须是菜单类型");
        Asserts.isTrue(equalsApp(parent.getAppCode(), appCode), ErrorCode.PARAM_ERROR,
                "上级权限必须属于同一应用");
    }

    private List<SsoPermission> childrenOf(Long id) {
        return permissionMapper.selectList(
                new LambdaQueryWrapper<SsoPermission>().eq(SsoPermission::getParentId, id));
    }

    /** candidate 是否在 rootId 的子树里（防 parentId 改成自己后代形成环） */
    private boolean isDescendant(Long rootId, Long candidate) {
        Long cursor = candidate;
        int guard = 0;
        while (cursor != null && cursor != 0L && guard++ < 32) {
            SsoPermission node = permissionMapper.selectById(cursor);
            if (node == null) {
                return false;
            }
            if (rootId.equals(node.getParentId())) {
                return true;
            }
            cursor = node.getParentId();
        }
        return false;
    }

    private boolean equalsApp(String a, String b) {
        String x = a == null ? PermissionConstants.PLATFORM_APP : a;
        String y = b == null ? PermissionConstants.PLATFORM_APP : b;
        return x.equals(y);
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }

    private PermissionVO toVO(SsoPermission p) {
        PermissionVO vo = new PermissionVO();
        vo.setId(p.getId());
        vo.setAppCode(p.getAppCode() == null ? PermissionConstants.PLATFORM_APP : p.getAppCode());
        vo.setCode(p.getCode());
        vo.setName(p.getName());
        vo.setType(p.getType());
        vo.setTypeDesc(PermissionType.of(p.getType() == null ? PermissionType.BUTTON.getCode() : p.getType()).getDesc());
        vo.setParentId(p.getParentId());
        vo.setSort(p.getSort());
        vo.setRemark(p.getRemark());
        vo.setBuiltin(PermissionConstants.isBuiltin(vo.getAppCode(), p.getCode()));
        return vo;
    }
}
