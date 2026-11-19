package com.laimengchao.sso.forum.module.poll.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class PollOptionVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String text;
    private Integer votes;
}
