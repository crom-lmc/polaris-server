package com.laimengchao.sso.forum.module.member.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MemberProfileVO extends MemberVO {

    private Integer favoriteCount;
    private Integer badgeCount;
}
