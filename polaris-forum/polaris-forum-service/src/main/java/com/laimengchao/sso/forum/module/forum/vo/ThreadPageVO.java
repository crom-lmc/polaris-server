package com.laimengchao.sso.forum.module.forum.vo;

import lombok.Data;

import java.util.List;

/** 帖子分页结果：列表 + 总数，供前台服务端分页渲染，避免一次返回全部正文。 */
@Data
public class ThreadPageVO {

    private List<ThreadVO> records;
    private long total;
    private int page;
    private int size;
}
