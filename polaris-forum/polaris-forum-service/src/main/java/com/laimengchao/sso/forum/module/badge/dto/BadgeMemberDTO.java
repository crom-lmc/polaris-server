package com.laimengchao.sso.forum.module.badge.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.laimengchao.sso.forum.module.badge.entity.Badge;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BadgeMemberDTO extends Badge {

    private String awardedAt;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long memberBadgeId;
}
