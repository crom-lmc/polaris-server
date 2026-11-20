package com.laimengchao.sso.forum.module.lottery.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class LotteryPrize {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String prizeKey;
    /** 奖项类型：POINTS 积分、GIFT 礼品 */
    private String prizeType;
    /** 关联 forum_gift.id，仅 GIFT 类型有效 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long giftId;
    private String name;
    private Integer points;
    private String color;
    private Integer weight;
    private Integer sortNo;
    private Boolean enabled;
    private String createdAt;
    private String updatedAt;
}
