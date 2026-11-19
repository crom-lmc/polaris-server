package com.laimengchao.sso.forum.module.admin.vo;

import com.laimengchao.sso.forum.module.forum.vo.CommentVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminCommentVO extends CommentVO {

    private String threadTitle;
}
