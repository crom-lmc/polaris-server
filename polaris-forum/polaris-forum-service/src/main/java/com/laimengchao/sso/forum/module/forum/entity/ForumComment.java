package com.laimengchao.sso.forum.module.forum.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ForumComment {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long threadId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long authorAccountId;
    private String authorName;
    private String content;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentCommentId;
    private Integer likes;
    private Integer deleted;
    private String createdAt;
}
