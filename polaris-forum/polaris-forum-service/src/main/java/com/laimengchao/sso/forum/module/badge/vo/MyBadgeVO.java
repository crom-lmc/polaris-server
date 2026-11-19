package com.laimengchao.sso.forum.module.badge.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MyBadgeVO extends BadgeVO {

    private String awardedAt;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long memberBadgeId;
    private Long currentValue;
    private Boolean awarded;
}
