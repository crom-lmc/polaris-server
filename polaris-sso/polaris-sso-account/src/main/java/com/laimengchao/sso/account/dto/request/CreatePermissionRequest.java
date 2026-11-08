package com.laimengchao.sso.account.dto.request;

import lombok.Data;

/**
 * 新建权限请求。
 *
 * <p>code 是后端 {@code @Permit} 与前端按钮显隐的共同契约，格式约定 {@code 资源:操作}
 * （如 {@code order:export}），只允许小写字母、数字、冒号、下划线、中划线。
 */
@Data
public class CreatePermissionRequest {

    /**
     * 所属应用编码。缺省 {@code sso}（平台自身）；接业务应用时填该应用在
     * sso_application 里的 code。唯一性只在应用内判定，不同应用可同码。
     */
    private String appCode;

    /** 权限码，应用内唯一。如 report:export */
    private String code;
    private String name;

    /** 1=菜单 2=按钮，默认按钮 */
    private Integer type;

    /** 上级权限ID（菜单做分组节点时填），顶级为 0 */
    private Long parentId;

    /**
     * 上级权限码（同应用内）。批量导入时用它代替 parentId——
     * 接入方手里只有自己定义的码，拿不到 SSO 内的自增 ID。
     */
    private String parentCode;

    private Integer sort;
    private String remark;
}
