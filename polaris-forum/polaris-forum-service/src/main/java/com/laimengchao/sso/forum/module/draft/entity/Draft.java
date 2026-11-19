package com.laimengchao.sso.forum.module.draft.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class Draft {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;
    private String kind;
    private String board;
    private String title;
    private String content;
    private String tags;
    private Integer reward;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long coverAttachmentId;
    private String pollConfig;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long publishedThreadId;
    private Integer deleted;
    private String createdAt;
    private String updatedAt;
}
