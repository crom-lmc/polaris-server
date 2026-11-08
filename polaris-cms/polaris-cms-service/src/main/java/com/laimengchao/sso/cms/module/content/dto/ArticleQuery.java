package com.laimengchao.sso.cms.module.content.dto;

import lombok.Data;

/** 文章列表/详情查询条件 */
@Data
public class ArticleQuery {

    /** 标题/摘要模糊匹配 */
    private String keyword;
    private Long categoryId;
    private Long tagId;
    /** null=全部状态（不含回收站）；-1=回收站；0..5=具体状态 */
    private Integer status;
    private Integer pinned;
    private Integer recommended;
    private Integer page = 1;
    private Integer size = 10;
}
