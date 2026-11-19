package com.laimengchao.sso.forum.module.member.dto;

import com.laimengchao.sso.forum.module.forum.entity.ForumMember;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MemberDetailDTO extends ForumMember {

    private String createdAt;
}
