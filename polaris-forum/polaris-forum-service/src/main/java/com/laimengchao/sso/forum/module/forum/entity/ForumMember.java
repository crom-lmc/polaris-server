package com.laimengchao.sso.forum.module.forum.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ForumMember {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;
    private String username;
    private String nickname;
    private String bio;
    private String location;
    private String website;
    private Integer points;
    private Integer threadsCount;
    private Integer likesReceived;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long avatarAttachmentId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long coverAttachmentId;
}
