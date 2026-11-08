package com.laimengchao.sso.account.controller;

import com.laimengchao.sso.account.constant.PermissionConstants;
import com.laimengchao.sso.account.dto.request.CreatePermissionRequest;
import com.laimengchao.sso.account.dto.request.UpdatePermissionRequest;
import com.laimengchao.sso.account.dto.response.PermissionVO;
import com.laimengchao.sso.account.security.Permit;
import com.laimengchao.sso.account.service.PermissionService;
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
 * 权限管理接口。权限点是「角色 -> 权限 -> 账户」链路的叶子，也是前后端唯一的权限契约。
 *
 * <p>所有查询都支持 {@code app} 参数做应用隔离：不传视为「全部应用」，
 * 传具体应用则只返回该应用下的权限树——业务应用的按钮码不会混进平台管理台。
 *
 * <p>内置权限码（被 {@code @Permit} 引用）不可删除、不可改码，只能改名与排序。
 */
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    /** @param app 应用编码，缺省为全部应用（sso 表示平台自身） */
    @GetMapping("/tree")
    @Permit(PermissionConstants.PERMISSION_LIST)
    public R<List<PermissionVO>> tree(@RequestParam(required = false) String app) {
        return R.ok(permissionService.tree(app));
    }

    @GetMapping("/all")
    @Permit(PermissionConstants.PERMISSION_LIST)
    public R<List<PermissionVO>> all(@RequestParam(required = false) String app) {
        return R.ok(permissionService.listAll(app));
    }

    @PostMapping
    @Permit(PermissionConstants.PERMISSION_CREATE)
    public R<PermissionVO> create(@RequestBody CreatePermissionRequest req) {
        return R.ok(permissionService.create(req));
    }

    @PutMapping("/{id}")
    @Permit(PermissionConstants.PERMISSION_EDIT)
    public R<PermissionVO> update(@PathVariable Long id, @RequestBody UpdatePermissionRequest req) {
        return R.ok(permissionService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionConstants.PERMISSION_DELETE)
    public R<Void> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return R.ok();
    }
}
