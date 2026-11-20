package com.laimengchao.sso.forum.module.forum.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ForumGift {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String name;
    private String category;
    private String description;
    private Integer points;
    private Integer stock;
    private String icon;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String imageUrl;
    /** 兑换所需最低社区等级，1-20 */
    private Integer minLevel;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer status;
}
