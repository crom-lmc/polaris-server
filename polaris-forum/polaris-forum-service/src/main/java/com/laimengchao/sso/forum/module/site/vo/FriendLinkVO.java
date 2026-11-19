package com.laimengchao.sso.forum.module.site.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class FriendLinkVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String name;
    private String url;
    private Integer sortNo;
    private Boolean enabled;
    private String createdAt;
    private String updatedAt;
}
