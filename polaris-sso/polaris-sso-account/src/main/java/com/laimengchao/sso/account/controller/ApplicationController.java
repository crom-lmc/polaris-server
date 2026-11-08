package com.laimengchao.sso.account.controller;

import com.laimengchao.sso.account.constant.PermissionConstants;
import com.laimengchao.sso.account.dto.request.ApplicationRequest;
import com.laimengchao.sso.account.dto.request.CreatePermissionRequest;
import com.laimengchao.sso.account.dto.response.ApplicationVO;
import com.laimengchao.sso.account.dto.response.PermissionVO;
import com.laimengchao.sso.account.security.Permit;
import com.laimengchao.sso.account.service.ApplicationService;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用注册管理。应用是「账号能进哪些业务系统」的粒度，
 * 与按钮级权限码正交：有 {@code app:list} 才能进这个页面，账号具体能进哪个应用看授权关系。
 */
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;
    private final PermissionService permissionService;

    @GetMapping
    @Permit(PermissionConstants.APP_LIST)
    public R<List<ApplicationVO>> list(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Integer status) {
        return R.ok(applicationService.list(keyword, status));
    }

    /** 启用中的应用（给「分配应用」下拉与前端「我的应用」用） */
    @GetMapping("/enabled")
    @Permit(PermissionConstants.APP_LIST)
    public R<List<ApplicationVO>> enabled() {
        return R.ok(applicationService.listEnabled());
    }

    @GetMapping("/{id}")
    @Permit(PermissionConstants.APP_LIST)
    public R<ApplicationVO> detail(@PathVariable Long id) {
        return R.ok(applicationService.detail(id));
    }

    @PostMapping
    @Permit(PermissionConstants.APP_CREATE)
    public R<ApplicationVO> create(@RequestBody ApplicationRequest req) {
        return R.ok(applicationService.create(req));
    }

    @PutMapping("/{id}")
    @Permit(PermissionConstants.APP_EDIT)
    public R<ApplicationVO> update(@PathVariable Long id, @RequestBody ApplicationRequest req) {
        return R.ok(applicationService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionConstants.APP_DELETE)
    public R<Void> delete(@PathVariable Long id) {
        applicationService.delete(id);
        return R.ok();
    }

    /** 重置应用密钥：返回新明文，旧密钥立刻失效（正在跑的应用需同步更新配置） */
    @PostMapping("/{id}/secret")
    @Permit(PermissionConstants.APP_SECRET_RESET)
    public R<String> resetSecret(@PathVariable Long id) {
        return R.ok(applicationService.resetSecret(id));
    }

    // ---------------- 应用接入：权限点清单与批量导入 ----------------

    /**
     * 某应用已注册的权限点清单（扁平，含菜单与按钮）。
     *
     * <p>用途：接入方上线前核对「我声明的按钮码，SSO 这边是不是都齐了」；
     * 也可用于 CI 里比对代码里 {@code @RequiresPerm} 的码与平台登记是否一致。
     *
     * @param code 应用编码（sso_application.code）
     */
    @GetMapping("/{code}/permissions")
    @Permit(PermissionConstants.PERMISSION_LIST)
    public R<List<PermissionVO>> permissions(@PathVariable String code) {
        return R.ok(permissionService.listAll(code));
    }

    /**
     * 批量导入某应用的权限点（幂等 upsert）。
     *
     * <p>这是「接入新应用」的主入口：业务应用把自己的按钮码清单一提交，权限树就长出来了，
     * 之后在角色管理里勾选即可完成授权，不需要改 SSO 的 Java 代码。
     * 已存在的码只刷新名称/排序/上级，不会重复建，也不会清掉角色已有授权。
     *
     * <p>请求体里的 appCode 会被忽略，一律以路径参数为准，避免越权写到别的应用。
     */
    @PostMapping("/{code}/permissions/import")
    @Permit(PermissionConstants.PERMISSION_CREATE)
    public R<Map<String, Object>> importPermissions(@PathVariable String code,
                                                         @RequestBody List<CreatePermissionRequest> items) {
        if (items != null) {
            items.forEach(i -> i.setAppCode(code));
        }
        int created = permissionService.importForApp(code, items);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("appCode", code);
        out.put("received", items == null ? 0 : items.size());
        out.put("created", created);
        out.put("updated", (items == null ? 0 : items.size()) - created);
        return R.ok(out);
    }
}
