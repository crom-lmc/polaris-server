package com.laimengchao.sso.cms.module.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * CMS 文章。status 状态机：
 * 0 草稿 →(提审)→ 6 审批中 →(审批中心通过)→ 3 已发布 / 2 待发布(定时)；审批驳回 4。
 * 1 待审核是本地审核时代的历史状态，新单据不再产生；5 已下线。
 */
@Data
@TableName("cms_article")
public class Article {

    public static final int STATUS_DRAFT = 0;
    /** 历史：本地审核的「待审核」，保留兼容老数据 */
    public static final int STATUS_PENDING = 1;
    public static final int STATUS_SCHEDULED = 2;
    public static final int STATUS_PUBLISHED = 3;
    public static final int STATUS_REJECTED = 4;
    public static final int STATUS_OFFLINE = 5;
    /** 已提审、在审批中心流转中（新增状态） */
    public static final int STATUS_APPROVING = 6;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String summary;
    private String content;
    private Long categoryId;
    /** 主标签ID（一篇一个标签，用于筛选） */
    private Long tagId;
    /** 标签名快照，多个用逗号分隔（历史多标签保留展示，新保存收敛为单个） */
    private String tag;
    private String cover;

    private Integer status;
    private Integer pinned;
    private Integer recommended;

    private String seoTitle;
    private String seoKeywords;
    private String seoDescription;

    /** 定时发布时间（status=2 时生效） */
    private LocalDateTime publishAt;
    private LocalDateTime publishedAt;

    private Long authorId;
    private String authorName;

    private String submitNote;
    private String auditNote;
    private String auditorName;
    private LocalDateTime auditAt;

    /** 审批中心实例ID：非空且 status=6 表示内容发布审批进行中 */
    private Long approvalInstanceId;

    /** 逻辑删除：1=回收站 */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public boolean published() {
        return status != null && status == STATUS_PUBLISHED;
    }
}
