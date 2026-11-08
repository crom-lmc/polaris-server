package com.laimengchao.sso.account.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限实体。type=1 菜单（页面级），type=2 按钮（操作级/按钮级权限）。
 * code 形如 account:create，是 @Permit 注解与前端按钮显隐的契约。
 *
 * <p>{@code appCode} 决定这条权限属于哪个应用：{@code sso} 是平台自身（内置 28 个码），
 * 其余对应 {@code sso_application.code}。唯一键是 {@code (app_code, code)} 而不是单列 code，
 * 这样博客的 {@code article:list} 和钓鱼后台的 {@code article:list} 可以共存互不干扰。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sso_permission")
public class SsoPermission extends BaseEntity {

    /** 所属应用编码；sso 表示平台自身 */
    private String appCode;

    /** 权限码，应用内唯一 */
    private String code;
    private String name;

    /** @see com.laimengchao.sso.common.enums.PermissionType */
    private Integer type;

    /** 上级权限ID（菜单树），顶级为 0 */
    private Long parentId;

    private Integer sort;
    private String remark;
}
