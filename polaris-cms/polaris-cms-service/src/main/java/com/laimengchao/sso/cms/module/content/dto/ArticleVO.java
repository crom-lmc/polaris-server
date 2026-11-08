package com.laimengchao.sso.cms.module.content.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 文章视图对象（列表与详情共用，详情多 content 与版本数） */
@Data
public class ArticleVO {

    private Long id;
    private String title;
    private String summary;
    private String content;
    private Long categoryId;
    private String categoryName;
    private String cover;

    private Integer status;
    private Integer pinned;
    private Integer recommended;

    private String seoTitle;
    private String seoKeywords;
    private String seoDescription;

    private LocalDateTime publishAt;
    private LocalDateTime publishedAt;

    private Long authorId;
    private String authorName;

    private String submitNote;
    private String auditNote;
    private String auditorName;
    private LocalDateTime auditAt;

    /** 审批中心实例ID（雪花ID序列化成字符串，前端按字符串处理） */
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(
            using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    private Long approvalInstanceId;

    /** 主标签ID（cms_tag 自增ID，与 categoryId 一样按数字返回） */
    private Long tagId;
    /** 标签名快照，多个用逗号分隔（历史多标签；新保存只有一个） */
    private String tag;

    private Long versionCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
