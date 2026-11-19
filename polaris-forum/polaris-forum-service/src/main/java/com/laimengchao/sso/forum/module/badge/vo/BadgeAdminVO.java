package com.laimengchao.sso.forum.module.badge.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BadgeAdminVO extends BadgeVO {

    private Long awardedCount;
}
