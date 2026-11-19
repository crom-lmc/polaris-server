package com.laimengchao.sso.forum.module.badge.dto;

import com.laimengchao.sso.forum.module.badge.entity.Badge;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BadgeAdminDTO extends Badge {

    private Long awardedCount;
}
