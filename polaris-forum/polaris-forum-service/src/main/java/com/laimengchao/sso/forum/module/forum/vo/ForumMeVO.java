package com.laimengchao.sso.forum.module.forum.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Set;

@Data
public class ForumMeVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;
    private String username;
    private boolean superAdmin;
    private String appCode;
    private Set<String> permissions;
    private ForumMemberVO member;
}
