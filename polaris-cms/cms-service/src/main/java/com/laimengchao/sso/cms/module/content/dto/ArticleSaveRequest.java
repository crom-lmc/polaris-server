package com.laimengchao.sso.cms.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/** 文章保存请求（新建草稿 / 编辑既有内容） */
@Data
public class ArticleSaveRequest {

    /** 编辑时传入；新建由控制器强制置 null */
    private Long id;

    @NotBlank(message = "标题不能为空")
    @Size(max = 128, message = "标题最长 128 字")
    private String title;

    @Size(max = 500, message = "摘要最长 500 字")
    private String summary;

    private String content;

    private Long categoryId;

    private String cover;

    /** 标签ID（一篇一个标签） */
    private Long tagId;

    @Size(max = 128)
    private String seoTitle;
    @Size(max = 255)
    private String seoKeywords;
    @Size(max = 500)
    private String seoDescription;

    private Integer pinned;
    private Integer recommended;

    /** 定时发布时间：非空且晚于当前时间时保存为「待发布」状态 */
    private LocalDateTime publishAt;
}
