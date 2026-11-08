package com.laimengchao.sso.account.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 账户视图对象（脱敏：不含密码哈希）。
 */
@Data
public class AccountVO {
    /** 雪花ID 转字符串，避免前端 19 位数字丢精度 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private Integer status;
    private String statusDesc;
    private String source;
    private Integer pwdResetRequired;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    /** 角色编码列表 */
    private List<String> roles;
}
