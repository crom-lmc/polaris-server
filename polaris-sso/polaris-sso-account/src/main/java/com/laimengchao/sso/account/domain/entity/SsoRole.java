package com.laimengchao.sso.account.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色实体。SUPER_ADMIN 为内置超级管理员角色，拥有全部权限且跳过 @Permit 校验。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sso_role")
public class SsoRole extends BaseEntity {

    /** 角色编码，如 SUPER_ADMIN / ADMIN / USER */
    private String code;
    /**
     * 角色归属的应用编码（sso 表示平台自身）。
     * 角色是「应用内的权限集合」：ehr 的角色只能挂 ehr 的权限点，
     * 这样在账号中心按应用管理时不会把别的应用的按钮混进来。
     */
    private String appCode;
    private String name;
    private String remark;
}
