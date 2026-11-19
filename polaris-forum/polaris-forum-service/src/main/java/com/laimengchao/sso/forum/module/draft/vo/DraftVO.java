package com.laimengchao.sso.forum.module.draft.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.laimengchao.sso.forum.module.attachment.vo.AttachmentVO;
import com.laimengchao.sso.forum.module.poll.dto.PollConfigDTO;
import lombok.Data;

import java.util.List;

@Data
public class DraftVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String kind;
    private String board;
    private String title;
    private String content;
    private List<String> tags;
    private int reward;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long coverAttachmentId;
    private PollConfigDTO pollConfig;
    private List<AttachmentVO> attachments;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long publishedThreadId;
    private String createdAt;
    private String updatedAt;
}
