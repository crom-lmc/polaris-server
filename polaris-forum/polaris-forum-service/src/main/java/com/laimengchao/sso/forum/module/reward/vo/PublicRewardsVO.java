package com.laimengchao.sso.forum.module.reward.vo;

import com.laimengchao.sso.forum.module.forum.entity.ForumGift;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PublicRewardsVO {

    private String mode;
    private List<PrizeVO> prizes;
    private List<ForumGift> gifts;
    private Integer lotteryCost;
    private Integer dailyLotteryLimit;
}
