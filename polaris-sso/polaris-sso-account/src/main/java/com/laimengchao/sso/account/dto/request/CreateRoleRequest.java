package com.laimengchao.sso.account.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 新建角色请求。
 */
@Data
public class CreateRoleRequest {
    /** 角色编码（唯一），如 ADMIN */
    private String code;
    /** 归属应用编码，缺省 sso（平台）。业务应用的角色填该应用 code，如 ehr */
    private String appCode;
    private String name;
    private String remark;
    /** 分配的权限ID列表 */
    private List<Long> permissionIds;
}
