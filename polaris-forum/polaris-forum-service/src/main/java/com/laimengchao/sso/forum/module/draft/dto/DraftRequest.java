package com.laimengchao.sso.forum.module.draft.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.laimengchao.sso.forum.module.poll.dto.PollConfigRequest;
import lombok.Data;

import java.util.List;

@Data
public class DraftRequest {

    private String kind;
    private String board;
    private String title;
    private String content;
    private List<Object> tags;
    private Object reward;
    private Object coverAttachmentId;
    private List<Object> attachmentIds;
    private PollConfigRequest pollConfig;
    private PollConfigRequest poll;
    @JsonIgnore
    private boolean pollConfigPresent;

    @JsonSetter("pollConfig")
    public void setPollConfig(PollConfigRequest pollConfig) {
        this.pollConfig = pollConfig;
        this.pollConfigPresent = true;
    }
}
