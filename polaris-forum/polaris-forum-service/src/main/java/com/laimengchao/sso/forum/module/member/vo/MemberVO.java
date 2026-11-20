package com.laimengchao.sso.forum.module.member.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class MemberVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;
    private String username;
    private String nickname;
    private String bio;
    private String location;
    private String website;
    private Integer points;
    private Long threadsCount;
    private Long likesReceived;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long avatarAttachmentId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long coverAttachmentId;
    private String createdAt;
    private String displayName;
    private String avatarUrl;
    private String coverUrl;
    private Long commentsCount;
    private Long totalPoints;
    /** 累计经验值，用于等级晋升 */
    private Long experience;
    private Integer level;
    private String levelName;
    private String levelColor;
    private Integer nextLevel;
    private Integer experienceToNext;
    private Integer levelProgress;
}
