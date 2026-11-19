package com.laimengchao.sso.forum.module.taxonomy.vo;

import lombok.Data;

import java.util.List;

@Data
public class BoardVO {

    private String name;
    private String description;
    private String color;
    private Integer sortNo;
    private String key;
    private Integer count;
    private List<TagVO> tags;
    private Boolean enabled;
    private String createdAt;
    private String updatedAt;
}
