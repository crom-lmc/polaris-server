package com.laimengchao.sso.forum.module.forum.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class ThreadVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String kind;
    private String board;
    private String title;
    private String content;
    private String excerpt;
    private List<String> tags;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long authorAccountId;
    private Integer reward;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long coverAttachmentId;
    private Integer replies;
    private Integer views;
    private Integer likes;
    private Boolean pinned;
    private Boolean solved;
    private Boolean recommended;
    private String recommendedAt;
    private String createdAt;
    private AuthorVO author;
    private String coverUrl;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean liked;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean favorited;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Date favoritedAt;
}
