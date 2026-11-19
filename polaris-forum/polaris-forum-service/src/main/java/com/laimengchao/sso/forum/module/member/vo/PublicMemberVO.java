package com.laimengchao.sso.forum.module.member.vo;

import com.laimengchao.sso.forum.module.badge.dto.BadgeAcquiredDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class PublicMemberVO extends MemberVO {

    private List<BadgeAcquiredDTO> badges;
    private Integer followingCount;
    private Integer followersCount;
    private Boolean following;
}
