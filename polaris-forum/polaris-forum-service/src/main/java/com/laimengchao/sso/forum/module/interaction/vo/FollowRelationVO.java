package com.laimengchao.sso.forum.module.interaction.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class FollowRelationVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;
    private String username;
    private String nickname;
    private String bio;
    private String location;
    private String website;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long avatarAttachmentId;
    private String followedAt;
    private String displayName;
    private String avatarUrl;
}
