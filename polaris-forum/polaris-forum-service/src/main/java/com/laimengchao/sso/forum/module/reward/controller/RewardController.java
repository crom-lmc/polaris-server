package com.laimengchao.sso.forum.module.reward.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.forum.module.forum.entity.ForumExchange;
import com.laimengchao.sso.forum.module.reward.service.RewardService;
import com.laimengchao.sso.forum.module.reward.vo.GiftExchangeVO;
import com.laimengchao.sso.forum.module.reward.vo.LotteryDrawVO;
import com.laimengchao.sso.forum.module.reward.vo.RewardSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/forum")
@RequiredArgsConstructor
public class RewardController {

    private final RewardService rewardService;

    @GetMapping("/points/summary")
    public R<RewardSummaryVO> summary() {
        return R.ok(rewardService.summary());
    }

    @PostMapping("/points/checkin")
    public R<RewardSummaryVO> checkin() {
        return R.ok(rewardService.checkin());
    }

    @PostMapping("/lottery/draw")
    public R<LotteryDrawVO> draw() {
        return R.ok(rewardService.draw());
    }

    @PostMapping("/gifts/{id}/exchange")
    public R<GiftExchangeVO> exchange(@PathVariable long id) {
        return R.ok(rewardService.exchange(id));
    }

    @GetMapping("/exchanges")
    public R<List<ForumExchange>> exchanges() {
        return R.ok(rewardService.exchanges());
    }
}
