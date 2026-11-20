package com.laimengchao.sso.forum.module.member.dto;

import lombok.Data;

@Data
public class PointRankingDTO {

    private Long accountId;
    private String username;
    private String nickname;
    private Long points;
    private Long totalPoints;
    private Long experience;
    private Long avatarAttachmentId;
}
