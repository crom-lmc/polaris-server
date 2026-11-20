package com.laimengchao.sso.forum.module.reward.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.settings.client.dto.EmbedPointDTO;
import com.laimengchao.sso.settings.client.support.SettingsClient;
import com.laimengchao.sso.forum.module.badge.service.BadgeService;
import com.laimengchao.sso.forum.module.forum.entity.ForumExchange;
import com.laimengchao.sso.forum.module.forum.entity.ForumGift;
import com.laimengchao.sso.forum.module.forum.entity.ForumLotteryLog;
import com.laimengchao.sso.forum.module.forum.entity.ForumPointLog;
import com.laimengchao.sso.forum.module.forum.mapper.ForumMapper;
import com.laimengchao.sso.forum.module.level.service.LevelService;
import com.laimengchao.sso.forum.module.lottery.service.LotteryService;
import com.laimengchao.sso.forum.module.reward.vo.FeatureFlagsVO;
import com.laimengchao.sso.forum.module.reward.vo.GiftExchangeVO;
import com.laimengchao.sso.forum.module.reward.vo.LotteryDrawVO;
import com.laimengchao.sso.forum.module.reward.vo.PrizeVO;
import com.laimengchao.sso.forum.module.reward.vo.PublicRewardsVO;
import com.laimengchao.sso.forum.module.reward.vo.RewardSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RewardService {

    private static final String PRIZE_GIFT = "GIFT";
    private static final String SOURCE_EXCHANGE = "EXCHANGE";
    private static final String SOURCE_LOTTERY = "LOTTERY";
    /** 配置中心埋点：功能开关，JSON 形如 {"enabled":true} */
    private static final String EMBED_LOTTERY_ENABLED = "forum.lottery.enabled";
    private static final String EMBED_EXCHANGE_ENABLED = "forum.exchange.enabled";

    private final ForumMapper mapper;
    private final BadgeService badgeService;
    private final LotteryService lotteryService;
    private final LevelService levelService;
    private final SettingsClient settingsClient;
    private final ObjectMapper objectMapper;

    public PublicRewardsVO publicRewards() {
        return new PublicRewardsVO(lotteryService.mode(), lotteryService.publicPrizes(),
                mapper.listGifts(), lotteryService.lotteryCost(), lotteryService.dailyLimit());
    }

    /** 供前台/后台判断入口、按钮是否置灰；开关来自配置中心埋点。 */
    public FeatureFlagsVO featureFlags() {
        return new FeatureFlagsVO(lotteryEnabled(), exchangeEnabled());
    }

    /** 抽奖开关：埋点缺失/解析失败/配置中心不可用时按开启处理，避免误伤线上功能。 */
    public boolean lotteryEnabled() {
        return flag(EMBED_LOTTERY_ENABLED);
    }

    /** 兑换开关：埋点缺失/解析失败/配置中心不可用时按开启处理。 */
    public boolean exchangeEnabled() {
        return flag(EMBED_EXCHANGE_ENABLED);
    }

    private boolean flag(String code) {
        EmbedPointDTO point = settingsClient.embedPoint(code);
        if (point == null || point.getValue() == null || point.getValue().isBlank()) return true;
        try {
            JsonNode root = objectMapper.readTree(point.getValue());
            JsonNode enabled = root.get("enabled");
            return enabled == null || enabled.isNull() || enabled.asBoolean(true);
        } catch (Exception e) {
            log.warn("配置中心埋点 {} 解析失败，按开启处理：{}", code, e.getMessage());
            return true;
        }
    }

    @Transactional
    public RewardSummaryVO summary() {
        SecurityUser user = requireUser();
        mapper.ensureMember(user.getAccountId(), user.getUsername());
        return new RewardSummaryVO(mapper.findMember(user.getAccountId()), mapper.pointLogs(user.getAccountId()),
                mapper.checkedInToday(user.getAccountId()) > 0, mapper.checkinDays(user.getAccountId()),
                Math.max(0, lotteryService.dailyLimit() - mapper.lotteryCountToday(user.getAccountId())));
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
        if (!lotteryEnabled()) throw new BizException(ErrorCode.FORBIDDEN, "积分抽奖功能暂未开启");
        int dailyLimit = lotteryService.dailyLimit();
        int cost = lotteryService.lotteryCost();
        int index = lotteryService.drawIndex();
        if (index < 0) throw new BizException(ErrorCode.CONFLICT, "抽奖奖项未配置，请联系管理员");
        PrizeVO prize = lotteryService.prizeAt(index);
        if (prize == null) throw new BizException(ErrorCode.CONFLICT, "抽奖奖项未配置，请联系管理员");
        SecurityUser user = requireUser();
        mapper.ensureMember(user.getAccountId(), user.getUsername());
        mapper.memberForUpdate(user.getAccountId());
        int used = mapper.lotteryCountToday(user.getAccountId());
        if (used >= dailyLimit) throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "今日抽奖次数已用完");
        changePoints(user.getAccountId(), -cost, "LOTTERY", "", "积分抽奖");
        int award = 0;
        if (PRIZE_GIFT.equals(prize.getPrizeType())) {
            award = awardGift(user.getAccountId(), prize);
        } else {
            award = prize.getPoints() == null ? 0 : prize.getPoints();
        }
        ForumLotteryLog log = new ForumLotteryLog();
        log.setId(IdWorker.getId());
        log.setAccountId(user.getAccountId());
        log.setPrizeKey(prize.getKey());
        log.setPrizeName(prize.getName());
        log.setCostPoints(cost);
        log.setAwardPoints(award);
        mapper.insertLottery(log);
        if (award > 0) {
            changePoints(user.getAccountId(), award, "LOTTERY_AWARD", String.valueOf(log.getId()), "抽奖获得" + prize.getName());
            badgeService.evaluate(user.getAccountId(), "TOTAL_POINTS");
        }
        return new LotteryDrawVO(index, prize, balance(user.getAccountId()), dailyLimit - used - 1);
    }

    /**
     * 抽中礼品奖项：扣减库存并写入兑换记录（来源=抽奖），与积分兑换共用后续发放流程。
     * 仅在并发导致扣库存失败时降级发放礼品等值积分，避免用户中奖却一无所获。
     */
    private int awardGift(long accountId, PrizeVO prize) {
        ForumGift gift = prize.getGiftId() == null ? null : mapper.giftForUpdate(prize.getGiftId());
        if (gift == null || !Integer.valueOf(1).equals(gift.getStatus())
                || gift.getStock() == null || gift.getStock() <= 0
                || mapper.decreaseGiftStock(gift.getId()) != 1) {
            return prize.getGiftPoints() == null ? 0 : prize.getGiftPoints();
        }
        ForumExchange exchange = new ForumExchange();
        exchange.setId(IdWorker.getId());
        exchange.setAccountId(accountId);
        exchange.setGiftId(gift.getId());
        exchange.setGiftName(gift.getName());
        exchange.setPoints(0);
        exchange.setSource(SOURCE_LOTTERY);
        mapper.insertExchange(exchange);
        return 0;
    }

    public List<ForumExchange> exchanges() {
        return mapper.listExchanges(requireUser().getAccountId());
    }

    @Transactional
    public GiftExchangeVO exchange(long giftId) {
        if (!exchangeEnabled()) throw new BizException(ErrorCode.FORBIDDEN, "礼品兑换功能暂未开启");
        SecurityUser user = requireUser();
        mapper.ensureMember(user.getAccountId(), user.getUsername());
        mapper.memberForUpdate(user.getAccountId());
        ForumGift gift = mapper.giftForUpdate(giftId);
        if (gift == null || gift.getStatus() != 1) throw new BizException(ErrorCode.NOT_FOUND, "礼品不存在或已下架");
        if (gift.getStock() <= 0) throw new BizException(ErrorCode.CONFLICT, "礼品库存不足");
        int minLevel = gift.getMinLevel() == null ? 1 : gift.getMinLevel();
        int myLevel = levelService.currentLevel(user.getAccountId());
        if (myLevel < minLevel) throw new BizException(ErrorCode.FORBIDDEN, "该礼品需 Lv." + minLevel + " 及以上才能兑换");
        int points = gift.getPoints();
        changePoints(user.getAccountId(), -points, "GIFT_EXCHANGE", String.valueOf(giftId), "兑换" + gift.getName());
        if (mapper.decreaseGiftStock(giftId) != 1) throw new BizException(ErrorCode.CONFLICT, "礼品库存不足");
        ForumExchange exchange = new ForumExchange();
        exchange.setId(IdWorker.getId());
        exchange.setAccountId(user.getAccountId());
        exchange.setGiftId(giftId);
        exchange.setGiftName(gift.getName());
        exchange.setPoints(points);
        exchange.setSource(SOURCE_EXCHANGE);
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
