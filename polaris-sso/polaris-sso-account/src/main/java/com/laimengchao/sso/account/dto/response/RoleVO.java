package com.laimengchao.sso.account.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色视图对象（含已分配的权限ID与权限码）。
 */
@Data
public class RoleVO {
    /** 雪花ID 转字符串，避免前端 19 位数字丢精度 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String code;
    /** 归属应用编码；sso 为平台角色 */
    private String appCode;
    private String name;
    private String remark;
    /** 已授权的权限ID：每个元素同样转字符串，否则授权抽屉回填后保存会把 id 改坏 */
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> permissionIds;
    private LocalDateTime createdAt;
    /** 是否内置角色（SUPER_ADMIN）：不可删除 */
    private Boolean builtin;
    /** 关联账户数，用于删除前提示 */
    private Long accountCount;
}
