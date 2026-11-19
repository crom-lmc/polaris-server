package com.laimengchao.sso.forum.module.reward.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PrizeVO {

    private String key;
    private String name;
    private Integer points;
    private String color;
}
