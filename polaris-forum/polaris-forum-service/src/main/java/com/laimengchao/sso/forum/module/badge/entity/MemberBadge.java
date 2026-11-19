package com.laimengchao.sso.forum.module.badge.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class MemberBadge {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long badgeId;
}
