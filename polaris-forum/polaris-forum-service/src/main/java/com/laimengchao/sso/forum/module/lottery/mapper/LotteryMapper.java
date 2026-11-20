package com.laimengchao.sso.forum.module.lottery.mapper;

import com.laimengchao.sso.forum.module.lottery.entity.LotteryConfig;
import com.laimengchao.sso.forum.module.lottery.entity.LotteryPrize;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LotteryMapper {

    LotteryConfig findConfig();

    int updateConfig(LotteryConfig values);

    List<LotteryPrize> listPrizes(@Param("enabledOnly") boolean enabledOnly);

    LotteryPrize findPrize(@Param("id") long id);

    LotteryPrize findPrizeByKey(@Param("key") String key);

    int insertPrize(LotteryPrize values);

    int updatePrize(LotteryPrize values);

    int updatePrizeWeight(LotteryPrize values);

    int deletePrize(@Param("id") long id);

    int countEnabledPrizes();

    int sumEnabledWeight();
}
