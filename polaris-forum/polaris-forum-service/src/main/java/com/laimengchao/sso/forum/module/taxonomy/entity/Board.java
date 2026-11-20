package com.laimengchao.sso.forum.module.taxonomy.entity;

import lombok.Data;

@Data
public class Board {

    private String boardKey;
    private String parentKey;
    private String name;
    private String description;
    private String color;
    private Integer sortNo;
    private Boolean enabled;
    private String createdAt;
    private String updatedAt;
}
