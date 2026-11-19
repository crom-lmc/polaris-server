package com.laimengchao.sso.forum.module.forum.vo;

import com.laimengchao.sso.forum.module.attachment.vo.AttachmentVO;
import com.laimengchao.sso.forum.module.poll.vo.PollVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ThreadDetailVO extends ThreadVO {

    private List<AttachmentVO> attachments;
    private PollVO poll;
}
