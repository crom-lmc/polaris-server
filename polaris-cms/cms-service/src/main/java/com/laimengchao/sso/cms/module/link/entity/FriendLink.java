package com.laimengchao.sso.cms.module.link.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 友情链接：站点名 + 地址 + 可选 Logo + 排序 + 启停 */
@Data
@TableName("cms_friend_link")
public class FriendLink {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String url;
    private String logo;
    private String description;
    private Integer sort;
    private Integer status;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
