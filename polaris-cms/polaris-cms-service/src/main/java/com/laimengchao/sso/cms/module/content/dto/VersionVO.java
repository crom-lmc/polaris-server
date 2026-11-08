package com.laimengchao.sso.cms.module.content.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 版本快照 VO（详情带 content，列表不带以省流量） */
@Data
public class VersionVO {

    private Long id;
    private Long articleId;
    private String title;
    private String summary;
    private String content;
    private String remark;
    private String operator;
    private LocalDateTime createdAt;
}
