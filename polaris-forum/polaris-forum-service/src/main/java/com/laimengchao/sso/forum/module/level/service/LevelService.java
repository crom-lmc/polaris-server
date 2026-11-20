package com.laimengchao.sso.forum.module.level.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.settings.client.dto.EmbedPointDTO;
import com.laimengchao.sso.settings.client.support.SettingsClient;
import com.laimengchao.sso.forum.module.level.dto.LevelProgress;
import com.laimengchao.sso.forum.module.level.dto.LevelRequest;
import com.laimengchao.sso.forum.module.level.entity.Level;
import com.laimengchao.sso.forum.module.level.mapper.LevelMapper;
import com.laimengchao.sso.forum.module.level.vo.LevelRouteVO;
import com.laimengchao.sso.forum.module.level.vo.LevelVO;
import com.laimengchao.sso.forum.module.member.dto.MemberStatsDTO;
import com.laimengchao.sso.forum.module.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LevelService {

    /** 配置中心的等级上限埋点 key（JSON：{"maxLevel": 20}） */
    private static final String EMBED_MAX_LEVEL = "forum.level.max-levels";
    private static final int DEFAULT_MAX_LEVEL = 20;

    private final SettingsClient settingsClient;
    private final ObjectMapper objectMapper;

    /**
     * 允许配置的最高等级序号，供礼品门槛等外部校验复用。
     * 从配置中心埋点读取（60s 缓存）；埋点缺失/JSON 非法/配置中心不可用时兜底 20。
     */
    public int maxLevel() {
        EmbedPointDTO point = settingsClient.embedPoint(EMBED_MAX_LEVEL);
        if (point == null || point.getValue() == null || point.getValue().isBlank()) {
            return DEFAULT_MAX_LEVEL;
        }
        try {
            JsonNode root = objectMapper.readTree(point.getValue());
            int value = root.path("maxLevel").asInt(DEFAULT_MAX_LEVEL);
            return value > 0 ? value : DEFAULT_MAX_LEVEL;
        } catch (Exception e) {
            log.warn("配置中心埋点 {} 解析失败，使用默认上限 {}：{}", EMBED_MAX_LEVEL, DEFAULT_MAX_LEVEL, e.getMessage());
            return DEFAULT_MAX_LEVEL;
        }
    }

    private final LevelMapper mapper;
    private final MemberMapper memberMapper;

    /** 当前用户的晋升路线：未登录时只返回等级列表，进度按 0 经验计算 */
    public LevelRouteVO currentRoute() {
        Long accountId = SecurityUtils.currentUserId();
        long experience = 0;
        if (accountId != null) {
            MemberStatsDTO stats = memberMapper.stats(accountId);
            if (stats != null && stats.getExperience() != null) experience = stats.getExperience();
        }
        return route(experience);
    }

    public List<LevelVO> adminList() {
        return mapper.listAll().stream().map(this::view).toList();
    }

    /** 启用的等级定义（按等级序号），供等级徽章展示 */
    public List<LevelVO> definitions() {
        return mapper.listEnabled().stream().map(this::view).toList();
    }

    /** 公开晋升路线：全部启用等级 + 按累计经验计算的当前进度 */
    public LevelRouteVO route(long experience) {
        List<Level> levels = mapper.listEnabled();
        LevelRouteVO route = new LevelRouteVO();
        route.setLevels(levels.stream().map(this::view).toList());
        route.setTotalExperience(experience);
        LevelProgress progress = progress(levels, experience);
        route.setCurrentLevel(progress.getLevel());
        route.setCurrentLevelName(progress.getLevelName());
        route.setCurrentLevelColor(progress.getLevelColor());
        route.setNextLevel(progress.getNextLevel());
        route.setNextLevelName(progress.getNextLevelName());
        route.setExperienceToNext(progress.getExperienceToNext());
        route.setProgress(progress.getProgress());
        return route;
    }

    /** 按当前等级配置计算某累计经验对应的等级与升级进度 */
    public LevelProgress progress(long experience) {
        return progress(mapper.listEnabled(), experience);
    }

    /**
     * 批量按累计经验计算等级：只查一次等级配置，避免排行榜逐行调用 {@link #progress(long)}
     * 造成 N+1（每行一次 listEnabled）。纯读+计算，天然幂等。
     */
    public List<LevelProgress> progressBatch(List<Long> experiences) {
        List<Level> levels = mapper.listEnabled();
        return experiences.stream().map(e -> progress(levels, e == null ? 0L : e)).toList();
    }

    /** 指定账号当前等级序号，供礼品兑换门槛等场景校验（无会员记录按 1 级） */
    public int currentLevel(long accountId) {
        MemberStatsDTO stats = memberMapper.stats(accountId);
        long experience = stats == null || stats.getExperience() == null ? 0 : stats.getExperience();
        Integer level = progress(experience).getLevel();
        return level == null ? 1 : level;
    }

    private LevelProgress progress(List<Level> levels, long experience) {
        LevelProgress result = new LevelProgress();
        if (levels.isEmpty()) {
            result.setLevel(1);
            result.setLevelName("社区成员");
            result.setLevelColor("#5b67f1");
            result.setNextLevel(null);
            result.setExperienceToNext(0);
            result.setProgress(100);
            return result;
        }
        Level current = levels.get(0);
        Level next = null;
        for (Level level : levels) {
            if (experience >= level.getMinExperience()) {
                current = level;
            } else {
                next = level;
                break;
            }
        }
        result.setLevel(current.getLevelNo());
        result.setLevelName(current.getName());
        result.setLevelColor(current.getColor());
        if (next == null) {
            result.setNextLevel(null);
            result.setExperienceToNext(0);
            result.setProgress(100);
            return result;
        }
        result.setNextLevel(next.getLevelNo());
        result.setNextLevelName(next.getName());
        long span = (long) next.getMinExperience() - current.getMinExperience();
        long gained = experience - current.getMinExperience();
        int value = span <= 0 ? 100 : (int) Math.max(0, Math.min(100, gained * 100 / span));
        result.setProgress(value);
        result.setExperienceToNext((int) Math.max(0, (long) next.getMinExperience() - experience));
        return result;
    }

    public LevelVO create(LevelRequest body) {
        int max = maxLevel();
        if (mapper.listAll().size() >= max) throw param("等级数量最多为 " + max + " 个，如需增加请到配置中心调整埋点 " + EMBED_MAX_LEVEL);
        Level values = validated(body, null);
        if (mapper.findByNo(values.getLevelNo()) != null) throw conflict("等级序号 Lv." + values.getLevelNo() + " 已存在");
        validateExperienceOrder(values.getLevelNo(), values.getMinExperience(), null);
        values.setId(IdWorker.getId());
        mapper.insert(values);
        return view(mapper.findById(values.getId()));
    }

    @Transactional
    public LevelVO update(long id, LevelRequest body) {
        Level current = requireLevel(id);
        Level values = validated(body, current);
        values.setId(id);
        if (values.getLevelNo() != current.getLevelNo()) {
            Level duplicate = mapper.findByNo(values.getLevelNo());
            if (duplicate != null && duplicate.getId() != id) throw conflict("等级序号 Lv." + values.getLevelNo() + " 已存在");
        }
        validateExperienceOrder(values.getLevelNo(), values.getMinExperience(), id);
        mapper.update(values);
        return view(mapper.findById(id));
    }

    /**
     * 校验经验门槛随等级序号严格递增：任一等级的经验都必须高于所有更低等级、低于所有更高等级。
     * 双向校验可避免只改本级时把上一级的约束破坏掉（例如把 Lv.3 改得比 Lv.4 还高）。
     */
    private void validateExperienceOrder(int levelNo, int minExperience, Long excludeId) {
        for (Level other : mapper.listAll()) {
            if (excludeId != null && excludeId.equals(other.getId())) continue;
            if (other.getLevelNo() < levelNo && other.getMinExperience() >= minExperience) {
                throw param("经验门槛必须高于上一级：Lv." + other.getLevelNo() + " 为 " + other.getMinExperience()
                        + "，当前 Lv." + levelNo + " 不能低于它");
            }
            if (other.getLevelNo() > levelNo && other.getMinExperience() <= minExperience) {
                throw param("经验门槛必须低于下一级：Lv." + other.getLevelNo() + " 为 " + other.getMinExperience()
                        + "，当前 Lv." + levelNo + " 不能高于它");
            }
        }
    }

    @Transactional
    public void delete(long id) {
        Level level = requireLevel(id);
        if (Boolean.TRUE.equals(level.getBuiltin())) throw conflict("内置等级不允许删除");
        if (mapper.delete(id) != 1) throw conflict("等级状态已变化");
    }

    private Level validated(LevelRequest body, Level current) {
        Level values = new Level();
        values.setLevelNo(intValue(field(body == null ? null : body.getLevelNo(),
                body != null && body.isLevelNoPresent(), current == null ? null : current.getLevelNo()), "等级序号", 1, maxLevel()));
        values.setName(requiredText(field(body == null ? null : body.getName(),
                body != null && body.isNamePresent(), current == null ? null : current.getName()), "等级名称", 80));
        values.setMinExperience(intValue(field(body == null ? null : body.getMinExperience(),
                body != null && body.isMinExperiencePresent(), current == null ? null : current.getMinExperience()), "累计经验门槛", 0, Integer.MAX_VALUE));
        String color = limitedText(field(body == null ? null : body.getColor(),
                body != null && body.isColorPresent(), current == null ? null : current.getColor()), "等级颜色", 16);
        if (color.isBlank()) color = "#5b67f1";
        if (!color.matches("^#[0-9a-fA-F]{6}$")) throw param("等级颜色应为 6 位十六进制颜色");
        values.setColor(color);
        values.setIcon(limitedText(field(body == null ? null : body.getIcon(),
                body != null && body.isIconPresent(), current == null ? null : current.getIcon()), "等级图标", 64));
        values.setDescription(limitedText(field(body == null ? null : body.getDescription(),
                body != null && body.isDescriptionPresent(), current == null ? null : current.getDescription()), "等级说明", 500));
        values.setPrivileges(limitedText(field(body == null ? null : body.getPrivileges(),
                body != null && body.isPrivilegesPresent(), current == null ? null : current.getPrivileges()), "等级权益", 500));
        values.setSortNo(intValue(field(body == null ? null : body.getSortNo(),
                body != null && body.isSortNoPresent(), current == null ? null : current.getSortNo()), "排序号", 0, 10000));
        boolean enabled = current == null || current.getEnabled();
        Object rawEnabled = field(body == null ? null : body.getEnabled(),
                body != null && body.isEnabledPresent(), current == null ? null : current.getEnabled());
        if (rawEnabled != null) enabled = boolValue(rawEnabled);
        values.setEnabled(enabled);
        return values;
    }

    private Object field(Object requested, boolean present, Object current) {
        return present || current == null ? requested : current;
    }

    private Level requireLevel(long id) {
        Level level = mapper.findById(id);
        if (level == null) throw new BizException(ErrorCode.NOT_FOUND, "等级不存在");
        return level;
    }

    private LevelVO view(Level row) {
        LevelVO view = new LevelVO();
        view.setId(row.getId());
        view.setLevelNo(row.getLevelNo());
        view.setName(row.getName());
        view.setMinExperience(row.getMinExperience());
        view.setColor(row.getColor());
        view.setIcon(row.getIcon());
        view.setDescription(row.getDescription());
        view.setPrivileges(row.getPrivileges());
        view.setSortNo(row.getSortNo());
        view.setEnabled(row.getEnabled());
        view.setBuiltin(row.getBuiltin());
        return view;
    }

    private String requiredText(Object raw, String name, int maxLength) {
        String value = limitedText(raw, name, maxLength);
        if (value.isBlank()) throw param(name + "不能为空");
        return value;
    }

    private String limitedText(Object raw, String name, int maxLength) {
        String value = text(raw).trim();
        if (value.length() > maxLength) throw param(name + "长度不能超过 " + maxLength + " 个字符");
        return value;
    }

    private int intValue(Object value, String name, int min, int max) {
        int result;
        if (value instanceof Number number) result = number.intValue();
        else {
            try { result = value == null || String.valueOf(value).isBlank() ? 0 : Integer.parseInt(String.valueOf(value)); }
            catch (NumberFormatException e) { throw param(name + "不正确"); }
        }
        if (result < min || result > max) throw param(name + "范围为 " + min + "-" + max);
        return result;
    }

    private boolean boolValue(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() > 0;
        return Set.of("true", "1", "yes", "on").contains(text(value).trim().toLowerCase());
    }

    private BizException param(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }

    private BizException conflict(String message) {
        return new BizException(ErrorCode.CONFLICT, message);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
