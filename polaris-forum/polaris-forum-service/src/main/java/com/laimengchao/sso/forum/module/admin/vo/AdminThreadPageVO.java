package com.laimengchao.sso.forum.module.admin.vo;

import lombok.Data;

import java.util.List;

/** 管理端内容分页结果：列表 + 总数，避免一次返回全部帖子正文。 */
@Data
public class AdminThreadPageVO {

    private List<AdminThreadVO> records;
    private long total;
    private int page;
    private int size;
}
