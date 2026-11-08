package com.laimengchao.sso.ehr.module.announcement.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 通知视图：管理端列表/详情与账号中心列表共用；正文只在详情接口返回 */
@Data
public class AnnouncementVO {

    private String id;
    /** 业务编号，如 NOTICE-20261108-0001 */
    private String announcementNo;
    private String title;
    private String summary;
    private String category;
    private String categoryName;
    /** 富文本 HTML；列表接口为 null */
    private String content;
    private Integer targetType;
    /** 按部门时的组织 ID（字符串，避免前端雪花 ID 精度丢失） */
    private List<String> targetOrgIds = new ArrayList<>();
    private String targetOrgNames;
    private Integer pinned;
    private Integer status;
    private String statusName;
    private LocalDateTime publishAt;
    private LocalDateTime offlineAt;
    private String publisherName;
    private Integer readCount;
    private Integer targetCount;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 当前登录人是否已读（账号中心列表用） */
    private Boolean read;
}
