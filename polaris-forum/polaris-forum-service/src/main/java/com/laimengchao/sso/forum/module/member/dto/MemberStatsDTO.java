package com.laimengchao.sso.forum.module.member.dto;

import lombok.Data;

@Data
public class MemberStatsDTO {

    private Long threadsCount;
    private Long commentsCount;
    private Long likesReceived;
    private Long totalPoints;
    /** 累计经验值，用于等级晋升（与积分相互独立） */
    private Long experience;
}
