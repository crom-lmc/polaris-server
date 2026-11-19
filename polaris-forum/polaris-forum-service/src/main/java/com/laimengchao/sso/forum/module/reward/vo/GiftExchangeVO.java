package com.laimengchao.sso.forum.module.reward.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GiftExchangeVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long exchangeId;
    private Integer balance;
    private String giftName;
}
