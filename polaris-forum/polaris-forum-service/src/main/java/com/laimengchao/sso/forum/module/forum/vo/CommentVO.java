package com.laimengchao.sso.forum.module.forum.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

@Data
public class CommentVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long threadId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long authorAccountId;
    private String content;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentCommentId;
    private Integer likes;
    private Boolean deleted;
    private String createdAt;
    private String time;
    private AuthorVO author;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean liked;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<CommentVO> replies;
}
