package com.laimengchao.sso.forum.module.forum.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class AuthorVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;
    private String name;
    private String initials;
    private String title;
    private String color;
    private String avatarUrl;
}
