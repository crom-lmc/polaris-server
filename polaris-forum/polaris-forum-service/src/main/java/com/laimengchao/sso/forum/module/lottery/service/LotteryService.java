package com.laimengchao.sso.forum.module.lottery.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.forum.module.forum.entity.ForumGift;
import com.laimengchao.sso.forum.module.forum.mapper.ForumMapper;
import com.laimengchao.sso.forum.module.lottery.dto.LotteryConfigRequest;
import com.laimengchao.sso.forum.module.lottery.dto.PrizeRequest;
import com.laimengchao.sso.forum.module.lottery.entity.LotteryConfig;
import com.laimengchao.sso.forum.module.lottery.entity.LotteryPrize;
import com.laimengchao.sso.forum.module.lottery.mapper.LotteryMapper;
import com.laimengchao.sso.forum.module.lottery.vo.LotteryConfigVO;
import com.laimengchao.sso.forum.module.lottery.vo.LotteryPrizeVO;
import com.laimengchao.sso.forum.module.reward.vo.PrizeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LotteryService {

    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z][a-z0-9-]{1,31}");
    private static final String MODE_SQUARE = "SQUARE";
    private static final String MODE_WHEEL = "WHEEL";
    private static final String TYPE_POINTS = "POINTS";
    private static final String TYPE_GIFT = "GIFT";
    private static final int MAX_SQUARE = 8;
    private static final int MAX_WHEEL = 12;
    /** 启用奖项权重合计必须是 100（百分比制） */
    private static final int WEIGHT_TOTAL = 100;

    private final LotteryMapper mapper;
    private final ForumMapper forumMapper;

    /** 当前上架礼品，按 ID 索引；礼品奖项通过它补齐展示信息并过滤无库存项。 */
    private Map<Long, ForumGift> giftMap() {
        Map<Long, ForumGift> gifts = new HashMap<>();
        for (ForumGift gift : forumMapper.listGifts()) {
            if (gift.getId() != null) gifts.put(gift.getId(), gift);
        }
        return gifts;
    }

    /** 实际可抽奖的启用奖项：礼品奖项要求礼品仍在架且有库存，保证盘面与中奖结果一致。 */
    private List<LotteryPrize> drawablePrizes(Map<Long, ForumGift> gifts) {
        return mapper.listPrizes(true).stream()
                .filter(prize -> !TYPE_GIFT.equals(prize.getPrizeType())
                        || (prize.getGiftId() != null && gifts.containsKey(prize.getGiftId())
                            && nvl(gifts.get(prize.getGiftId()).getStock()) > 0))
                .toList();
    }

    /** 当前抽奖形态，缺省按方形九宫格。 */
    public String mode() {
        LotteryConfig config = requireConfig();
        return MODE_WHEEL.equals(config.getMode()) ? MODE_WHEEL : MODE_SQUARE;
    }

    public int lotteryCost() {
        return normalizeCost(requireConfig().getLotteryCost());
    }

    public int dailyLimit() {
        return normalizeLimit(requireConfig().getDailyLimit());
    }

    /** 公开抽奖盘的奖项（仅启用，按 sort_no 顺序，过滤无库存礼品）。 */
    public List<PrizeVO> publicPrizes() {
        Map<Long, ForumGift> gifts = giftMap();
        return drawablePrizes(gifts).stream().map(prize -> publicPrize(prize, gifts)).toList();
    }

    /**
     * 按权重随机抽取奖项，返回其在启用奖项有序列表中的下标（供前台定位格子/扇区）。
     * 没有启用奖项时不抛异常，由调用方给出业务错误。
     */
    public int drawIndex() {
        List<LotteryPrize> prizes = drawablePrizes(giftMap());
        if (prizes.isEmpty()) return -1;
        int total = 0;
        for (LotteryPrize prize : prizes) {
            total += Math.max(0, nvl(prize.getWeight()));
        }
        if (total <= 0) return ThreadLocalRandom.current().nextInt(prizes.size());
        int hit = ThreadLocalRandom.current().nextInt(total);
        int cursor = 0;
        for (int i = 0; i < prizes.size(); i++) {
            cursor += Math.max(0, nvl(prizes.get(i).getWeight()));
            if (hit < cursor) return i;
        }
        return prizes.size() - 1;
    }

    /** 取可抽奖项中指定下标的奖项（与 drawIndex 使用同一有序列表）。 */
    public PrizeVO prizeAt(int index) {
        Map<Long, ForumGift> gifts = giftMap();
        List<LotteryPrize> prizes = drawablePrizes(gifts);
        if (index < 0 || index >= prizes.size()) return null;
        return publicPrize(prizes.get(index), gifts);
    }

    private PrizeVO publicPrize(LotteryPrize prize, Map<Long, ForumGift> gifts) {
        ForumGift gift = TYPE_GIFT.equals(prize.getPrizeType()) && prize.getGiftId() != null
                ? gifts.get(prize.getGiftId()) : null;
        return new PrizeVO(prize.getPrizeKey(), prize.getName(), nvl(prize.getPoints()), prize.getColor(),
                gift == null ? TYPE_POINTS : TYPE_GIFT,
                gift == null ? null : gift.getId(),
                gift == null ? null : gift.getName(),
                gift == null ? null : gift.getImageUrl(),
                gift == null ? null : gift.getPoints());
    }

    public LotteryConfigVO adminConfig() {
        LotteryConfig config = requireConfig();
        String mode = MODE_WHEEL.equals(config.getMode()) ? MODE_WHEEL : MODE_SQUARE;
        List<ForumGift> gifts = forumMapper.listGifts();
        Map<Long, ForumGift> giftIndex = new HashMap<>();
        for (ForumGift gift : gifts) {
            if (gift.getId() != null) giftIndex.put(gift.getId(), gift);
        }
        List<LotteryPrizeVO> prizes = mapper.listPrizes(false).stream()
                .map(prize -> adminPrize(prize, giftIndex)).toList();
        return new LotteryConfigVO(mode, normalizeCost(config.getLotteryCost()),
                normalizeLimit(config.getDailyLimit()), maxPrizes(mode), mapper.sumEnabledWeight(), prizes, gifts);
    }

    public LotteryConfigVO updateConfig(LotteryConfigRequest body) {
        LotteryConfig current = requireConfig();
        String mode = body.isModePresent() ? parseMode(body.getMode()) : current.getMode();
        int cost = body.isLotteryCostPresent() ? positive(body.getLotteryCost(), "每次消耗积分", 1, 100000) : nvl(current.getLotteryCost());
        int limit = body.isDailyLimitPresent() ? positive(body.getDailyLimit(), "每日抽奖次数", 1, 1000) : nvl(current.getDailyLimit());
        if (cost <= 0) cost = 50;
        if (limit <= 0) limit = 3;
        // 切换形态时校验当前启用奖项数是否超过目标上限
        int enabledCount = mapper.countEnabledPrizes();
        int max = maxPrizes(mode);
        if (enabledCount > max) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    (MODE_WHEEL.equals(mode) ? "转盘" : "方形") + "最多支持 " + max + " 个奖项，请先停用多余奖项");
        }
        LotteryConfig values = new LotteryConfig();
        values.setMode(mode);
        values.setLotteryCost(cost);
        values.setDailyLimit(limit);
        mapper.updateConfig(values);
        return adminConfig();
    }

    /**
     * 把启用奖项权重按现有比例缩放，使合计恰好为 100（百分比制）。
     * 最大余数法保证整数且总和精确为 100；不改变相对概率，仅归一化数值。
     */
    public void normalizeWeights() {
        List<LotteryPrize> enabled = mapper.listPrizes(true);
        if (enabled.isEmpty()) return;
        int[] weights = new int[enabled.size()];
        int total = 0;
        for (int i = 0; i < enabled.size(); i++) {
            weights[i] = Math.max(0, nvl(enabled.get(i).getWeight()));
            total += weights[i];
        }
        int[] result = scaleToHundred(weights, total);
        for (int i = 0; i < enabled.size(); i++) {
            if (result[i] != weights[i]) {
                LotteryPrize values = new LotteryPrize();
                values.setId(enabled.get(i).getId());
                values.setWeight(result[i]);
                mapper.updatePrizeWeight(values);
            }
        }
    }

    /** 按权重占比分配到总和 100，返回每项整数权重（最大余数法）。total<=0 时平均分配。 */
    private int[] scaleToHundred(int[] weights, int total) {
        int n = weights.length;
        int[] result = new int[n];
        if (total <= 0) {
            int base = WEIGHT_TOTAL / n;
            int rest = WEIGHT_TOTAL - base * n;
            for (int i = 0; i < n; i++) result[i] = base + (i < rest ? 1 : 0);
            return result;
        }
        double[] exact = new double[n];
        int assigned = 0;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            exact[i] = weights[i] * 100.0 / total;
            result[i] = (int) Math.floor(exact[i]);
            assigned += result[i];
            order[i] = i;
        }
        // 余数大的优先补齐，保证总和精确为 100
        Arrays.sort(order, (a, b) -> Double.compare(exact[b] - result[b], exact[a] - result[a]));
        int rest = WEIGHT_TOTAL - assigned;
        for (int i = 0; i < rest; i++) result[order[i]] += 1;
        return result;
    }

    /** 校验变更后的启用奖项权重合计不超过 100。excludeId/excludeWeight 为编辑时被替换项。 */
    private void checkWeightTotal(int newWeight, long excludeId) {
        int current = mapper.sumEnabledWeight();
        if (excludeId > 0) {
            LotteryPrize old = mapper.findPrize(excludeId);
            if (old != null && Boolean.TRUE.equals(old.getEnabled())) current -= nvl(old.getWeight());
        }
        int total = current + Math.max(0, newWeight);
        if (total > WEIGHT_TOTAL) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "启用奖项的权重合计不能超过 " + WEIGHT_TOTAL + "（当前操作后将达到 " + total + "）");
        }
    }

    public LotteryPrizeVO createPrize(PrizeRequest body) {
        if (!body.isKeyPresent() || !body.isNamePresent()) throw param("奖项标识和名称必填");
        String key = key(body.getKey());
        if (mapper.findPrizeByKey(key) != null) throw param("奖项标识已存在");
        String type = body.isPrizeTypePresent() ? parseType(body.getPrizeType()) : TYPE_POINTS;
        Long giftId = null;
        if (TYPE_GIFT.equals(type)) {
            if (!body.isGiftIdPresent()) throw param("礼品奖项必须选择礼品");
            giftId = parseGiftId(body.getGiftId());
            requireGift(giftId);
        }
        String name = name(body.getName());
        int points = TYPE_GIFT.equals(type) ? 0
                : body.isPointsPresent() ? nonNegative(body.getPoints(), "中奖积分", 0, 1000000) : 0;
        String color = body.isColorPresent() ? color(body.getColor()) : "#eef0ff";
        int weight = body.isWeightPresent() ? positive(body.getWeight(), "中奖权重", 0, WEIGHT_TOTAL) : 1;
        int sortNo = body.isSortNoPresent() ? nonNegative(body.getSortNo(), "排序号", 0, 9999) : 0;
        boolean enabled = !body.isEnabledPresent() || bool(body.getEnabled());
        checkCapacity(enabled, 0);
        if (enabled) checkWeightTotal(weight, 0);
        LotteryPrize values = new LotteryPrize();
        values.setId(IdWorker.getId());
        values.setPrizeKey(key);
        values.setPrizeType(type);
        values.setGiftId(giftId);
        values.setName(name);
        values.setPoints(points);
        values.setColor(color);
        values.setWeight(weight);
        values.setSortNo(sortNo);
        values.setEnabled(enabled);
        mapper.insertPrize(values);
        return adminPrize(require(values.getId()), giftMap());
    }

    public LotteryPrizeVO updatePrize(long id, PrizeRequest body) {
        LotteryPrize current = require(id);
        String type = body.isPrizeTypePresent() ? parseType(body.getPrizeType()) : normalizeType(current.getPrizeType());
        Long giftId = null;
        if (TYPE_GIFT.equals(type)) {
            if (body.isGiftIdPresent()) {
                giftId = parseGiftId(body.getGiftId());
                requireGift(giftId);
            } else {
                // 编辑权重等非礼品字段时，允许保留已下架礼品的原关联（盘面会自动过滤）
                giftId = current.getGiftId();
                if (giftId == null) throw param("礼品奖项必须选择礼品");
            }
        }
        String name = body.isNamePresent() ? name(body.getName()) : current.getName();
        int points = TYPE_GIFT.equals(type) ? 0
                : body.isPointsPresent() ? nonNegative(body.getPoints(), "中奖积分", 0, 1000000) : nvl(current.getPoints());
        String color = body.isColorPresent() ? color(body.getColor()) : current.getColor();
        int weight = body.isWeightPresent() ? positive(body.getWeight(), "中奖权重", 0, WEIGHT_TOTAL) : nvl(current.getWeight());
        int sortNo = body.isSortNoPresent() ? nonNegative(body.getSortNo(), "排序号", 0, 9999) : nvl(current.getSortNo());
        boolean enabled = body.isEnabledPresent() ? bool(body.getEnabled()) : Boolean.TRUE.equals(current.getEnabled());
        boolean wasEnabled = Boolean.TRUE.equals(current.getEnabled());
        checkCapacity(enabled, wasEnabled ? 1 : 0);
        // 变更后为启用态则校验合计（checkWeightTotal 内部会按旧状态减掉原权重）
        if (enabled) checkWeightTotal(weight, id);
        LotteryPrize values = new LotteryPrize();
        values.setId(id);
        values.setPrizeType(type);
        values.setGiftId(giftId);
        values.setName(name);
        values.setPoints(points);
        values.setColor(color);
        values.setWeight(weight);
        values.setSortNo(sortNo);
        values.setEnabled(enabled);
        mapper.updatePrize(values);
        return adminPrize(require(id), giftMap());
    }

    public void deletePrize(long id) {
        require(id);
        mapper.deletePrize(id);
    }

    /** 校验当前形态下启用奖项数不超限；exclude 为本次变更前已占用的名额（编辑启用->启用时为 1）。 */
    private void checkCapacity(boolean enabling, int exclude) {
        if (!enabling) return;
        int enabledCount = mapper.countEnabledPrizes() - exclude;
        int max = maxPrizes(mode());
        if (enabledCount >= max) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    (MODE_WHEEL.equals(mode()) ? "转盘" : "方形") + "最多支持 " + max + " 个奖项，请先停用其他奖项");
        }
    }

    private int maxPrizes(String mode) {
        return MODE_WHEEL.equals(mode) ? MAX_WHEEL : MAX_SQUARE;
    }

    private LotteryConfig requireConfig() {
        LotteryConfig config = mapper.findConfig();
        if (config == null) {
            // 初始化器通常已播种；万一缺行，兜底返回默认值，避免抽奖接口报错
            config = new LotteryConfig();
            config.setId(1);
            config.setMode(MODE_SQUARE);
            config.setLotteryCost(50);
            config.setDailyLimit(3);
        }
        return config;
    }

    private LotteryPrize require(long id) {
        LotteryPrize prize = mapper.findPrize(id);
        if (prize == null) throw new BizException(ErrorCode.NOT_FOUND, "奖项不存在");
        return prize;
    }

    private LotteryPrizeVO adminPrize(LotteryPrize row, Map<Long, ForumGift> gifts) {
        ForumGift gift = TYPE_GIFT.equals(row.getPrizeType()) && row.getGiftId() != null
                ? gifts.get(row.getGiftId()) : null;
        String type = TYPE_GIFT.equals(row.getPrizeType()) ? TYPE_GIFT : TYPE_POINTS;
        return new LotteryPrizeVO(row.getId(), row.getPrizeKey(), type,
                TYPE_GIFT.equals(type) ? row.getGiftId() : null,
                gift == null ? null : gift.getName(),
                gift == null ? null : gift.getImageUrl(),
                gift == null ? null : gift.getPoints(),
                gift == null ? null : gift.getStock(),
                row.getName(), row.getPoints(), row.getColor(),
                row.getWeight(), row.getSortNo(), Boolean.TRUE.equals(row.getEnabled()),
                row.getCreatedAt(), row.getUpdatedAt());
    }

    private String parseType(Object raw) {
        String value = text(raw).trim().toUpperCase();
        if (!TYPE_POINTS.equals(value) && !TYPE_GIFT.equals(value)) throw param("奖项类型仅支持 POINTS 或 GIFT");
        return value;
    }

    private String normalizeType(String raw) {
        return TYPE_GIFT.equals(raw) ? TYPE_GIFT : TYPE_POINTS;
    }

    private long parseGiftId(Object raw) {
        if (raw instanceof Number number) return number.longValue();
        String value = text(raw).trim();
        if (value.isEmpty()) throw param("礼品奖项必须选择礼品");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw param("礼品ID格式不正确");
        }
    }

    private void requireGift(long giftId) {
        if (!giftMap().containsKey(giftId)) throw param("礼品不存在或已下架，请先上架礼品");
    }

    private String parseMode(Object raw) {
        String value = text(raw).trim().toUpperCase();
        if (!MODE_SQUARE.equals(value) && !MODE_WHEEL.equals(value)) throw param("抽奖形态仅支持 SQUARE 或 WHEEL");
        return value;
    }

    private String key(Object raw) {
        String value = text(raw).trim();
        if (!KEY_PATTERN.matcher(value).matches()) throw param("奖项标识应为 2-32 位小写字母、数字或连字符，且以字母开头");
        return value;
    }

    private String name(Object raw) {
        String value = text(raw).trim();
        if (value.isEmpty() || value.length() > 80) throw param("奖项名称长度应为 1-80 个字符");
        return value;
    }

    private String color(Object raw) {
        String value = text(raw).trim();
        if (!value.matches("#[0-9a-fA-F]{6}")) throw param("颜色需为 #RRGGBB 格式");
        return value;
    }

    private boolean bool(Object raw) {
        if (raw instanceof Boolean b) return b;
        String value = text(raw);
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) return Boolean.parseBoolean(value);
        throw param("enabled 必须为布尔值");
    }

    private int positive(Object raw, String label, int min, int max) {
        int value = toInt(raw, -1);
        if (value < min || value > max) throw param(label + "应为 " + min + " 到 " + max + " 的整数");
        return value;
    }

    private int nonNegative(Object raw, String label, int min, int max) {
        return positive(raw, label, min, max);
    }

    private int toInt(Object raw, int fallback) {
        if (raw instanceof Number number) return number.intValue();
        try {
            String value = text(raw).trim();
            return value.isEmpty() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int normalizeCost(Integer value) {
        return value != null && value > 0 ? value : 50;
    }

    private int normalizeLimit(Integer value) {
        return value != null && value > 0 ? value : 3;
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private BizException param(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }
}
