package com.laimengchao.sso.account.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

/**
 * 权限视图对象（菜单树节点：菜单下挂按钮子节点）。
 */
@Data
public class PermissionVO {
    /** 雪花ID 转字符串，避免前端 19 位数字丢精度 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 所属应用编码：sso 是平台自身，其余对应 sso_application.code */
    private String appCode;

    private String code;
    private String name;
    private Integer type;
    private String typeDesc;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;
    private Integer sort;
    private String remark;
    /** 是否内置权限码：内置码被 @Permit 引用，不可删除 */
    private Boolean builtin;
    private List<PermissionVO> children;
}
