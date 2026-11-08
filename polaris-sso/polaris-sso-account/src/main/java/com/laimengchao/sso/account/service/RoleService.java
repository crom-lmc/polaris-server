package com.laimengchao.sso.account.service;

import com.laimengchao.sso.account.dto.request.CreateRoleRequest;
import com.laimengchao.sso.account.dto.request.UpdateRoleRequest;
import com.laimengchao.sso.account.dto.response.RoleVO;
import com.laimengchao.sso.common.PageResult;

import java.util.List;

public interface RoleService {

    /** appCode 为空表示不按应用过滤 */
    PageResult<RoleVO> page(int page, int size, String keyword, String appCode);

    List<RoleVO> list(String appCode);

    RoleVO detail(Long id);

    RoleVO create(CreateRoleRequest req);

    RoleVO update(Long id, UpdateRoleRequest req);

    void delete(Long id);

    /** 全量覆盖角色权限 */
    void assignPermissions(Long id, List<Long> permissionIds);
}
