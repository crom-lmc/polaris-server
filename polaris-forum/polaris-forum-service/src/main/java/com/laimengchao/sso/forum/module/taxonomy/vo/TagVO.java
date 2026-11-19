package com.laimengchao.sso.forum.module.taxonomy.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class TagVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String boardKey;
    private String name;
    private Integer sortNo;
    private Boolean enabled;
    private Integer count;
}
