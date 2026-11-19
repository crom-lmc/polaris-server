package com.laimengchao.sso.forum.module.poll.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PollConfigDTO {

    private String mode;
    private int maxChoices;
    private List<String> options;
}
