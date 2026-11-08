package com.laimengchao.sso.cms.module.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 文章版本快照：发布/回滚时写入，一键回滚的数据源 */
@Data
@TableName("cms_article_version")
public class ArticleVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;
    private String title;
    private String summary;
    private String content;
    /** 快照说明：发布快照 / 回滚自 vX */
    private String remark;
    private String operator;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
