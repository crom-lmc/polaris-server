package com.laimengchao.sso.forum.module.reward.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 论坛功能开关（抽奖/兑换），由配置中心埋点下发 */
@Data
@AllArgsConstructor
public class FeatureFlagsVO {

    private boolean lotteryEnabled;
    private boolean exchangeEnabled;
}
