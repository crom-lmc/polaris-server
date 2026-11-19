package com.laimengchao.sso.forum.module.reward.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.forum.module.badge.service.BadgeService;
import com.laimengchao.sso.forum.module.forum.entity.ForumExchange;
import com.laimengchao.sso.forum.module.forum.entity.ForumGift;
import com.laimengchao.sso.forum.module.forum.entity.ForumLotteryLog;
import com.laimengchao.sso.forum.module.forum.entity.ForumPointLog;
import com.laimengchao.sso.forum.module.forum.mapper.ForumMapper;
import com.laimengchao.sso.forum.module.reward.vo.GiftExchangeVO;
import com.laimengchao.sso.forum.module.reward.vo.LotteryDrawVO;
import com.laimengchao.sso.forum.module.reward.vo.PrizeVO;
import com.laimengchao.sso.forum.module.reward.vo.PublicRewardsVO;
import com.laimengchao.sso.forum.module.reward.vo.RewardSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class RewardService {

    private static final int LOTTERY_COST = 50;
    private static final int DAILY_LOTTERY_LIMIT = 3;
    private static final List<PrizeVO> PRIZES = List.of(
            new PrizeVO("points10", "10 积分", 10, "#eef0ff"), new PrizeVO("thanks", "谢谢参与", 0, "#f6f7fb"),
            new PrizeVO("points30", "30 积分", 30, "#eafaf5"), new PrizeVO("points5", "5 积分", 5, "#fff5e9"),
            new PrizeVO("points100", "100 积分", 100, "#fff0f6"), new PrizeVO("points20", "20 积分", 20, "#eef0ff"),
            new PrizeVO("thanks2", "再接再厉", 0, "#f6f7fb"), new PrizeVO("points50", "50 积分", 50, "#e6f4ff"));

    private final ForumMapper mapper;
    private final BadgeService badgeService;

    public PublicRewardsVO publicRewards() {
        return new PublicRewardsVO(PRIZES, mapper.listGifts(), LOTTERY_COST, DAILY_LOTTERY_LIMIT);
    }

    @Transactional
    public RewardSummaryVO summary() {
        SecurityUser user = requireUser();
        mapper.ensureMember(user.getAccountId(), user.getUsername());
        return new RewardSummaryVO(mapper.findMember(user.getAccountId()), mapper.pointLogs(user.getAccountId()),
                mapper.checkedInToday(user.getAccountId()) > 0, mapper.checkinDays(user.getAccountId()),
                Math.max(0, DAILY_LOTTERY_LIMIT - mapper.lotteryCountToday(user.getAccountId())));
    }

    @Transactional
    public RewardSummaryVO checkin() {
        SecurityUser user = requireUser();
        mapper.ensureMember(user.getAccountId(), user.getUsername());
        mapper.memberForUpdate(user.getAccountId());
        if (mapper.checkin(IdWorker.getId(), user.getAccountId()) != 1) {
            throw new BizException(ErrorCode.CONFLICT, "今天已经签到过了");
        }
        changePoints(user.getAccountId(), 10, "CHECKIN", "", "每日签到");
        badgeService.evaluate(user.getAccountId(), "CHECKIN_DAYS", "TOTAL_POINTS");
        return summary();
    }

    @Transactional
    public LotteryDrawVO draw() {
        SecurityUser user = requireUser();
        mapper.ensureMember(user.getAccountId(), user.getUsername());
        mapper.memberForUpdate(user.getAccountId());
        int used = mapper.lotteryCountToday(user.getAccountId());
        if (used >= DAILY_LOTTERY_LIMIT) throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "今日抽奖次数已用完");
        changePoints(user.getAccountId(), -LOTTERY_COST, "LOTTERY", "", "积分抽奖");
        int index = ThreadLocalRandom.current().nextInt(PRIZES.size());
        PrizeVO prize = PRIZES.get(index);
        int award = prize.getPoints();
        ForumLotteryLog log = new ForumLotteryLog();
        log.setId(IdWorker.getId());
        log.setAccountId(user.getAccountId());
        log.setPrizeKey(prize.getKey());
        log.setPrizeName(prize.getName());
        log.setAwardPoints(award);
        mapper.insertLottery(log);
        if (award > 0) {
            changePoints(user.getAccountId(), award, "LOTTERY_AWARD", String.valueOf(log.getId()), "抽奖获得" + prize.getName());
            badgeService.evaluate(user.getAccountId(), "TOTAL_POINTS");
        }
        return new LotteryDrawVO(index, prize, balance(user.getAccountId()), DAILY_LOTTERY_LIMIT - used - 1);
    }

    public List<ForumExchange> exchanges() {
        return mapper.listExchanges(requireUser().getAccountId());
    }

    @Transactional
    public GiftExchangeVO exchange(long giftId) {
        SecurityUser user = requireUser();
        mapper.ensureMember(user.getAccountId(), user.getUsername());
        mapper.memberForUpdate(user.getAccountId());
        ForumGift gift = mapper.giftForUpdate(giftId);
        if (gift == null || gift.getStatus() != 1) throw new BizException(ErrorCode.NOT_FOUND, "礼品不存在或已下架");
        if (gift.getStock() <= 0) throw new BizException(ErrorCode.CONFLICT, "礼品库存不足");
        int points = gift.getPoints();
        changePoints(user.getAccountId(), -points, "GIFT_EXCHANGE", String.valueOf(giftId), "兑换" + gift.getName());
        if (mapper.decreaseGiftStock(giftId) != 1) throw new BizException(ErrorCode.CONFLICT, "礼品库存不足");
        ForumExchange exchange = new ForumExchange();
        exchange.setId(IdWorker.getId());
        exchange.setAccountId(user.getAccountId());
        exchange.setGiftId(giftId);
        exchange.setGiftName(gift.getName());
        exchange.setPoints(points);
        mapper.insertExchange(exchange);
        return new GiftExchangeVO(exchange.getId(), balance(user.getAccountId()), gift.getName());
    }

    private void changePoints(long accountId, int delta, String bizType, String bizId, String remark) {
        if (mapper.changePoints(accountId, delta) != 1) throw new BizException(ErrorCode.CONFLICT, "可用积分不足");
        ForumPointLog log = new ForumPointLog();
        log.setId(IdWorker.getId());
        log.setAccountId(accountId);
        log.setChangePoints(delta);
        log.setBalance(balance(accountId));
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setRemark(remark);
        mapper.insertPointLog(log);
    }

    private int balance(long accountId) {
        return mapper.findMember(accountId).getPoints();
    }

    private SecurityUser requireUser() {
        return SecurityUtils.currentUser().orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
    }
}
