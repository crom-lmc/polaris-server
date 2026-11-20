package com.laimengchao.sso.forum.module.admin.vo;

import lombok.Data;

import java.util.List;

/** 管理端评论分页结果：列表 + 总数，避免一次返回全部评论正文。 */
@Data
public class AdminCommentPageVO {

    private List<AdminCommentVO> records;
    private long total;
    private int page;
    private int size;
}
