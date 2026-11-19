package com.laimengchao.sso.forum.module.poll.dto;

import lombok.Data;

import java.util.List;

@Data
public class PollVoteRequest {

    private List<Object> optionIds;
}
