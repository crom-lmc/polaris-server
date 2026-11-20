package com.laimengchao.sso.forum.module.lottery.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LotteryPrizeVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String key;
    /** 奖项类型：POINTS 积分、GIFT 礼品 */
    private String prizeType;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long giftId;
    /** 礼品名称/图片/所需积分/库存，仅 GIFT 类型返回，供后台配置展示 */
    private String giftName;
    private String giftImageUrl;
    private Integer giftPoints;
    private Integer giftStock;
    private String name;
    private Integer points;
    private String color;
    private Integer weight;
    private Integer sortNo;
    private Boolean enabled;
    private String createdAt;
    private String updatedAt;
}
