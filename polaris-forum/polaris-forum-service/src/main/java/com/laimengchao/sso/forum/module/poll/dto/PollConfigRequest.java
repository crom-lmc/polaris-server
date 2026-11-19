package com.laimengchao.sso.forum.module.poll.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;

@Data
public class PollConfigRequest {

    private Object enabled;
    private String mode;
    private Object maxChoices;
    private List<JsonNode> options;
}
