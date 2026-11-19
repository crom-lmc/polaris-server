package com.laimengchao.sso.forum.module.forum.dto;

import com.laimengchao.sso.forum.module.poll.dto.PollConfigRequest;
import lombok.Data;

import java.util.List;

@Data
public class ThreadPublishRequest {

    private String kind;
    private String board;
    private String title;
    private String content;
    private List<Object> tags;
    private Object reward;
    private Object coverAttachmentId;
    private List<Object> attachmentIds;
    private PollConfigRequest poll;
}
