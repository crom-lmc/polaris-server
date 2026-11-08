package com.laimengchao.sso.cms.module.portal.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 门户首页布局：一行一个区块模块（Banner/精选/最新/分类/标签/友情链接） */
@Data
@TableName("cms_portal_layout")
public class PortalLayout {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 模块标识，前端据此决定取数与渲染方式 */
    private String moduleKey;
    private String moduleName;
    /** 1启用 0隐藏 */
    private Integer enabled;
    /** 排序，小的在前 */
    private Integer sort;
    /** 模块参数 JSON，如 {"limit":6} */
    private String config;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
