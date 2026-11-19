package com.laimengchao.sso.forum.module.poll.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

@Data
public class PollVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String mode;
    private int maxChoices;
    private int totalVotes;
    private boolean voted;
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> selectedOptionIds;
    private List<PollOptionVO> options;
}
