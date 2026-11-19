package com.laimengchao.sso.forum.module.admin.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class SensitiveWord {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String word;
    private Boolean enabled;
    private String createdAt;
    private String updatedAt;
}
