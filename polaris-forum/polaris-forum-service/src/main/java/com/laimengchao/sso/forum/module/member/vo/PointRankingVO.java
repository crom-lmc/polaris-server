package com.laimengchao.sso.forum.module.member.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class PointRankingVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;
    private String name;
    private Long points;
    private Long totalPoints;
    private Integer level;
    private String avatarUrl;
}
