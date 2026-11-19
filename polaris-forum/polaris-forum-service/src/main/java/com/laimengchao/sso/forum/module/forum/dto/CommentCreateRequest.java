package com.laimengchao.sso.forum.module.forum.dto;

import lombok.Data;

@Data
public class CommentCreateRequest {

    private Object parentCommentId;
    private String content;
}
