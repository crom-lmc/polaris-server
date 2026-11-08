package com.laimengchao.sso.account.controller;

import com.laimengchao.sso.account.constant.PermissionConstants;
import com.laimengchao.sso.account.dto.request.CreateRoleRequest;
import com.laimengchao.sso.account.dto.request.UpdateRoleRequest;
import com.laimengchao.sso.account.dto.response.RoleVO;
import com.laimengchao.sso.account.security.Permit;
import com.laimengchao.sso.account.service.RoleService;
import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理接口。按钮级权限码见 @Permit。
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @Permit(PermissionConstants.ROLE_LIST)
    public R<PageResult<RoleVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String appCode) {
        return R.ok(roleService.page(page, size, keyword, appCode));
    }

    /** appCode 可选：只取某个应用的角色（账号编辑里按应用分组时用） */
    @GetMapping("/all")
    @Permit(PermissionConstants.ROLE_LIST)
    public R<List<RoleVO>> all(@RequestParam(required = false) String appCode) {
        return R.ok(roleService.list(appCode));
    }

    @GetMapping("/{id}")
    @Permit(PermissionConstants.ROLE_LIST)
    public R<RoleVO> detail(@PathVariable Long id) {
        return R.ok(roleService.detail(id));
    }

    @PostMapping
    @Permit(PermissionConstants.ROLE_CREATE)
    public R<RoleVO> create(@RequestBody CreateRoleRequest req) {
        return R.ok(roleService.create(req));
    }

    @PutMapping("/{id}")
    @Permit(PermissionConstants.ROLE_EDIT)
    public R<RoleVO> update(@PathVariable Long id, @RequestBody UpdateRoleRequest req) {
        return R.ok(roleService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionConstants.ROLE_DELETE)
    public R<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return R.ok();
    }

    @PutMapping("/{id}/permissions")
    @Permit(PermissionConstants.ROLE_ASSIGN)
    public R<Void> assign(@PathVariable Long id, @RequestBody List<Long> permissionIds) {
        roleService.assignPermissions(id, permissionIds);
        return R.ok();
    }
}
