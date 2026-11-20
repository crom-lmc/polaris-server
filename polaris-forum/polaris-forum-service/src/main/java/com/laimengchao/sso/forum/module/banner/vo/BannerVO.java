package com.laimengchao.sso.forum.module.banner.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class BannerVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String title;
    /** 轮播图片公开访问地址，空附件时为 null */
    private String imageUrl;
    private String linkUrl;
    private Integer sortNo;
    private Boolean enabled;
    private String createdAt;
    private String updatedAt;
}
