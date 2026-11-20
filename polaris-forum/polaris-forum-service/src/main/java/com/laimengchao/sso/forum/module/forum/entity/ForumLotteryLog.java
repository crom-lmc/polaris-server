package com.laimengchao.sso.forum.module.forum.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ForumLotteryLog {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;
    private String prizeKey;
    private String prizeName;
    private Integer costPoints;
    private Integer awardPoints;
}
