package com.laimengchao.sso.forum.module.forum.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ForumThread {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String kind;
    private String board;
    private String title;
    private String content;
    private String excerpt;
    private String tags;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long authorAccountId;
    private String authorName;
    private Integer reward;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long coverAttachmentId;
    private Integer replies;
    private Integer views;
    private Integer likes;
    private Integer pinned;
    private Integer solved;
    private Integer recommended;
    private String recommendedAt;
    private String createdAt;
    private Integer deleted;
}
