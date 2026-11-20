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
    /** 累计经验：等级排行按此排序，积分排行也一并返回便于展示 */
    private Long experience;
    private Integer level;
    private String levelName;
    private String levelColor;
    private String avatarUrl;
}
