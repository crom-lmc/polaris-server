package com.laimengchao.sso.ehr.module.announcement.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 内部通知（人力中心发布 → 统一账号中心「通知中心」展示）。
 * <p>
 * 通知目标是「全员（集团）」或「按部门（含下级）」，因此存的是组织 ID 逗号串 + 名称快照：
 * 员工调岗、部门改名后已发布的通知仍按发出时的范围展示，不会被后来的人事变动篡改。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_announcement")
public class Announcement extends BaseEntity {

    /** 业务编号，按 settings 的单号规则生成（前缀 NOTICE） */
    private String announcementNo;
    private String title;
    /** 摘要：列表里展示，避免把富文本正文塞进表格 */
    private String summary;
    /** 分类：取值见配置中心字典 ehr.announcement_category（HOLIDAY 节假日 / COMMENDATION 表彰 / DEPARTMENT 部门 / POLICY 制度 / APPOINTMENT 部门任命 / OTHER 其他） */
    private String category;
    /** 富文本正文（HTML） */
    private String content;
    /** 0 全员（集团） / 1 按部门 */
    private Integer targetType;
    /** 目标组织 ID，逗号分隔；targetType=0 时为空 */
    private String targetOrgIds;
    /** 目标组织名称快照，逗号分隔 */
    private String targetOrgNames;
    /** 1 置顶（列表优先展示） */
    private Integer pinned;
    /** 0 编辑中 / 1 已发布 / 2 已下架 */
    private Integer status;
    private LocalDateTime publishAt;
    private LocalDateTime offlineAt;
    private Long publisherAccountId;
    private String publisherName;
    /** 阅读人数（每账号首次打开详情记一次） */
    private Integer readCount;
    /** 应读人数（发布时按目标范围快照） */
    private Integer targetCount;
    private String remark;
}
