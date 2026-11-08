package com.laimengchao.sso.cms.module.content.dto;

import lombok.Data;

/** 分类/标签 VO：articleCount 用于占用提示与前台聚合页 */
@Data
public class TagVO {

    private Long id;
    private String name;
    private Integer sort;
    private Long articleCount;

    public static TagVO of(Long id, String name, long count) {
        TagVO vo = new TagVO();
        vo.id = id;
        vo.name = name;
        vo.articleCount = count;
        return vo;
    }
}
