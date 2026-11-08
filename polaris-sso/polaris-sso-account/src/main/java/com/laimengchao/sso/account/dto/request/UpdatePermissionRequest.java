package com.laimengchao.sso.account.dto.request;

import lombok.Data;

/**
 * 编辑权限请求。字段为 null 表示不改。
 *
 * <p>内置权限的 code 不允许修改——它被 {@code @Permit} 硬引用，改名等于让接口失联。
 */
@Data
public class UpdatePermissionRequest {

    private String code;
    private String name;
    private Integer type;
    private Long parentId;
    private Integer sort;
    private String remark;
}
