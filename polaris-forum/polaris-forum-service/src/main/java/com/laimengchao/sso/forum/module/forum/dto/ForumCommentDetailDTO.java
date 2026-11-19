package com.laimengchao.sso.forum.module.forum.dto;

import com.laimengchao.sso.forum.module.forum.entity.ForumComment;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ForumCommentDetailDTO extends ForumComment {

    private String threadTitle;
}
