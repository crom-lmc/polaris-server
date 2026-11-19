package com.laimengchao.sso.forum.module.interaction.dto;

import com.laimengchao.sso.forum.module.forum.entity.ForumMember;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FollowRelationDTO extends ForumMember {

    private String followedAt;
}
