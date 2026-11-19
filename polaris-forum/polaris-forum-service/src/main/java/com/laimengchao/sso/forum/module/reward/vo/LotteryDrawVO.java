package com.laimengchao.sso.forum.module.reward.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LotteryDrawVO {

    private Integer index;
    private PrizeVO prize;
    private Integer balance;
    private Integer remaining;
}
