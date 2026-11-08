package com.laimengchao.sso.account.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 编辑角色请求。
 */
@Data
public class UpdateRoleRequest {
    private String name;
    private String remark;
    /** 全量覆盖的权限ID列表 */
    private List<Long> permissionIds;
}
