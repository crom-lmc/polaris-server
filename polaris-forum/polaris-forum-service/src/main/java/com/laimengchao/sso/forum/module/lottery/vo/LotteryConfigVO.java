package com.laimengchao.sso.forum.module.lottery.vo;

import com.laimengchao.sso.forum.module.forum.entity.ForumGift;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class LotteryConfigVO {

    private String mode;
    private Integer lotteryCost;
    private Integer dailyLimit;
    private Integer maxPrizes;
    /** 启用奖项权重合计（百分比制，目标为 100） */
    private Integer weightTotal;
    private List<LotteryPrizeVO> prizes;
    /** 上架礼品列表，供后台把礼品添加为抽奖奖项时选择 */
    private List<ForumGift> gifts;
}
