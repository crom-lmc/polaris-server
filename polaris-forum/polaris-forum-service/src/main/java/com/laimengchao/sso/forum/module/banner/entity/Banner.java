package com.laimengchao.sso.forum.module.banner.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class Banner {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String title;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long imageAttachmentId;
    private String linkUrl;
    private Integer sortNo;
    private Boolean enabled;
    private String createdAt;
    private String updatedAt;
}
