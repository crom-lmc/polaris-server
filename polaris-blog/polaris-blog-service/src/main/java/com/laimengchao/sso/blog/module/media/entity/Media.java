package com.laimengchao.sso.blog.module.media.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 媒体文件（t_media）。
 */
@Data
@TableName("t_media")
public class Media implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fileName;

    private String originalName;

    private String url;

    private String mimeType;

    private Long size;

    private Integer width;

    private Integer height;

    private String folder;

    /** 逻辑目录 ID（t_media_folder），NULL 为未分组 */
    private Long folderId;

    /** 上传人：SSO 账户 ID（19 位，序列化为字符串避免前端精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long uploaderId;

    private LocalDateTime createdAt;
}
