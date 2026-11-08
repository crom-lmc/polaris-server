package com.laimengchao.sso.account.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 角色-权限关联（无业务字段，仅主键复合）。
 */
@Data
@TableName("sso_role_permission")
public class SsoRolePermission implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long roleId;
    private Long permissionId;
}
