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
    private String parentKey;
    private Integer count;
    private List<TagVO> tags;
    private List<BoardVO> children;
    private Boolean enabled;
    private String createdAt;
    private String updatedAt;
}
