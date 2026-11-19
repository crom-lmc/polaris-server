package com.laimengchao.sso.forum.module.member.dto;

import lombok.Data;

@Data
public class MemberStatsDTO {

    private Long threadsCount;
    private Long commentsCount;
    private Long likesReceived;
    private Long totalPoints;
}
