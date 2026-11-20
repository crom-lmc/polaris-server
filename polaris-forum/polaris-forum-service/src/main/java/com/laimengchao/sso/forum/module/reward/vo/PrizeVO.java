package com.laimengchao.sso.forum.module.reward.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PrizeVO {

    private String key;
    private String name;
    private Integer points;
    private String color;
    /** POINTS 积分、GIFT 礼品 */
    private String prizeType;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long giftId;
    private String giftName;
    private String giftImageUrl;
    /** 礼品所需积分，库存不足降级发积分时使用 */
    private Integer giftPoints;
}
