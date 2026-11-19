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
    private Integer level;
}
