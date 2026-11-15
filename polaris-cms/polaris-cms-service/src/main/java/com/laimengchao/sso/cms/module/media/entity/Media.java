package com.laimengchao.sso.cms.module.media.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cms_media")
public class Media {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String filename;
    private String originalName;
    private String url;
    private String contentType;
    private Long size;
    private Long uploadedBy;
    private String uploadedName;

    /** 所属组织编码（数据权限冗余字段） */
    private String orgCode;

    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
