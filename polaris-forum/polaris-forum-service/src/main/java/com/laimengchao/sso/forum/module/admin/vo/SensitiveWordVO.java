package com.laimengchao.sso.forum.module.admin.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class SensitiveWordVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String word;
    private Boolean enabled;
    private String createdAt;
    private String updatedAt;
}
