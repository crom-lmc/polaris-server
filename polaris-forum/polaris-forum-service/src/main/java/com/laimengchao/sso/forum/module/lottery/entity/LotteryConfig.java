package com.laimengchao.sso.forum.module.lottery.entity;

import lombok.Data;

@Data
public class LotteryConfig {

    private Integer id;
    private String mode;
    private Integer lotteryCost;
    private Integer dailyLimit;
    private String updatedAt;
}
