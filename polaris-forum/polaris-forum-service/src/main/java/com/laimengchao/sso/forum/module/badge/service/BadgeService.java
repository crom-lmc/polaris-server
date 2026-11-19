package com.laimengchao.sso.forum.module.badge.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.forum.module.badge.dto.BadgeAdminDTO;
import com.laimengchao.sso.forum.module.badge.dto.BadgeMemberDTO;
import com.laimengchao.sso.forum.module.badge.dto.BadgeRequest;
import com.laimengchao.sso.forum.module.badge.entity.Badge;
import com.laimengchao.sso.forum.module.badge.entity.MemberBadge;
import com.laimengchao.sso.forum.module.badge.mapper.BadgeMapper;
import com.laimengchao.sso.forum.module.badge.vo.BadgeAdminVO;
import com.laimengchao.sso.forum.module.badge.vo.BadgeVO;
import com.laimengchao.sso.forum.module.badge.vo.MyBadgeVO;
import com.laimengchao.sso.forum.module.notification.service.NotificationService;
import com.laimengchao.sso.forum.security.ForumAdminGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BadgeService {

    private static final Set<String> RULE_TYPES = Set.of(
            "TOTAL_POINTS", "THREAD_COUNT", "COMMENT_COUNT", "CHECKIN_DAYS", "LIKES_RECEIVED");
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "OFFLINE");

    private final BadgeMapper mapper;
    private final NotificationService notificationService;
    private final ForumAdminGuard guard;

    public List<BadgeVO> publicBadges() {
        return mapper.listPublished().stream().map(this::view).toList();
    }

    @Transactional
    public List<MyBadgeVO> myBadges() {
        SecurityUser user = requireUser();
        evaluate(user.getAccountId());
        return mapper.listForMember(user.getAccountId()).stream()
                .map(badge -> myView(user.getAccountId(), badge)).toList();
    }

    public List<BadgeAdminVO> adminList(String status) {
        guard.require("forum:badge");
        String value = status == null ? "" : status.trim().toUpperCase();
        if (!value.isBlank() && !STATUSES.contains(value)) throw param("勋章状态不正确");
        return mapper.listAdmin(value).stream()
                .map(row -> adminView(row, row.getAwardedCount())).toList();
    }

    public BadgeVO create(BadgeRequest body) {
        SecurityUser user = guard.require("forum:badge");
        Badge values = validated(body, null);
        values.setId(IdWorker.getId());
        values.setCreatedBy(user.getAccountId());
        mapper.insert(values);
        return view(mapper.findById(values.getId()));
    }

    @Transactional
    public BadgeVO update(long id, BadgeRequest body) {
        guard.require("forum:badge");
        Badge current = requireBadge(id);
        if (!"DRAFT".equals(current.getStatus())) throw new BizException(ErrorCode.CONFLICT, "仅草稿勋章可以修改");
        Badge values = validated(body, current);
        values.setId(id);
        if (mapper.updateDraft(values) != 1) throw new BizException(ErrorCode.CONFLICT, "勋章状态已变化");
        return view(mapper.findById(id));
    }

    @Transactional
    public BadgeAdminVO publish(long id) {
        guard.require("forum:badge");
        Badge badge = requireBadge(id);
        if (!"DRAFT".equals(badge.getStatus())) throw new BizException(ErrorCode.CONFLICT, "仅草稿勋章可以发布");
        validated(null, badge);
        if (mapper.publish(id) != 1) throw new BizException(ErrorCode.CONFLICT, "勋章状态已变化");
        for (Long accountId : mapper.listAccountIds()) {
            evaluateBadge(accountId, mapper.findById(id));
        }
        Badge result = mapper.findById(id);
        Long awardedCount = mapper.listAdmin("").stream()
                .filter(row -> row.getId() == id).findFirst().map(BadgeAdminDTO::getAwardedCount).orElse(0L);
        return adminView(result, awardedCount);
    }

    @Transactional
    public BadgeVO offline(long id) {
        guard.require("forum:badge");
        Badge badge = requireBadge(id);
        if (!"PUBLISHED".equals(badge.getStatus())) throw new BizException(ErrorCode.CONFLICT, "仅已发布勋章可以下线");
        if (mapper.offline(id) != 1) throw new BizException(ErrorCode.CONFLICT, "勋章状态已变化");
        return view(mapper.findById(id));
    }

    public void evaluate(long accountId, String... ruleTypes) {
        List<String> rules = ruleTypes == null ? List.of() : List.of(ruleTypes);
        for (Badge badge : mapper.listPublishedForRules(rules)) {
            evaluateBadge(accountId, badge);
        }
    }

    private void evaluateBadge(long accountId, Badge badge) {
        long currentValue = metric(accountId, badge.getRuleType());
        if (currentValue < badge.getThreshold()) return;
        MemberBadge award = new MemberBadge();
        award.setId(IdWorker.getId());
        award.setAccountId(accountId);
        award.setBadgeId(badge.getId());
        if (mapper.award(award) == 1) {
            notificationService.onBadgeAwarded(accountId, award.getId(), badge);
        }
    }

    private long metric(long accountId, String ruleType) {
        return switch (ruleType) {
            case "TOTAL_POINTS" -> mapper.totalPoints(accountId);
            case "THREAD_COUNT" -> mapper.threadCount(accountId);
            case "COMMENT_COUNT" -> mapper.commentCount(accountId);
            case "CHECKIN_DAYS" -> mapper.checkinDays(accountId);
            case "LIKES_RECEIVED" -> mapper.likesReceived(accountId);
            default -> throw param("不支持的勋章规则");
        };
    }

    private Badge validated(BadgeRequest body, Badge current) {
        Badge values = new Badge();
        values.setName(requiredText(field(body == null ? null : body.getName(),
                body != null && body.isNamePresent(), current == null ? null : current.getName()), "勋章名称", 80));
        values.setDescription(limitedText(field(body == null ? null : body.getDescription(),
                body != null && body.isDescriptionPresent(), current == null ? null : current.getDescription()), "勋章说明", 500));
        values.setIcon(limitedText(field(body == null ? null : body.getIcon(),
                body != null && body.isIconPresent(), current == null ? null : current.getIcon()), "勋章图标", 64));
        String color = limitedText(field(body == null ? null : body.getColor(),
                body != null && body.isColorPresent(), current == null ? null : current.getColor()), "勋章颜色", 16);
        if (!color.isBlank() && !color.matches("^#[0-9a-fA-F]{6}$")) throw param("勋章颜色应为 6 位十六进制颜色");
        values.setColor(color);
        values.setSortNo(intValue(field(body == null ? null : body.getSortNo(),
                body != null && body.isSortNoPresent(), current == null ? null : current.getSortNo())));
        String ruleType = text(field(body == null ? null : body.getRuleType(),
                body != null && body.isRuleTypePresent(), current == null ? null : current.getRuleType())).trim().toUpperCase();
        if (!RULE_TYPES.contains(ruleType)) throw param("不支持的勋章规则");
        values.setRuleType(ruleType);
        long threshold = longValue(field(body == null ? null : body.getThreshold(),
                body != null && body.isThresholdPresent(), current == null ? null : current.getThreshold()));
        if (threshold <= 0 || threshold > Integer.MAX_VALUE) throw param("勋章阈值必须为正整数");
        values.setThreshold((int) threshold);
        return values;
    }

    private Object field(Object requested, boolean present, Object current) {
        return present || current == null ? requested : current;
    }

    private Badge requireBadge(long id) {
        Badge badge = mapper.findForUpdate(id);
        if (badge == null) throw new BizException(ErrorCode.NOT_FOUND, "勋章不存在");
        return badge;
    }

    private BadgeVO view(Badge row) {
        return copy(row, new BadgeVO());
    }

    private BadgeAdminVO adminView(Badge row, Long awardedCount) {
        BadgeAdminVO view = copy(row, new BadgeAdminVO());
        view.setAwardedCount(awardedCount);
        return view;
    }

    private MyBadgeVO myView(long accountId, BadgeMemberDTO row) {
        MyBadgeVO view = copy(row, new MyBadgeVO());
        view.setAwardedAt(row.getAwardedAt());
        view.setMemberBadgeId(row.getMemberBadgeId());
        view.setCurrentValue(metric(accountId, row.getRuleType()));
        view.setAwarded(row.getMemberBadgeId() != null);
        return view;
    }

    private <T extends BadgeVO> T copy(Badge row, T view) {
        view.setId(row.getId());
        view.setName(row.getName());
        view.setDescription(row.getDescription());
        view.setIcon(row.getIcon());
        view.setColor(row.getColor());
        view.setSortNo(row.getSortNo());
        view.setRuleType(row.getRuleType());
        view.setThreshold(row.getThreshold());
        view.setStatus(row.getStatus());
        view.setCreatedBy(row.getCreatedBy());
        view.setPublishedAt(row.getPublishedAt());
        view.setCreatedAt(row.getCreatedAt());
        view.setUpdatedAt(row.getUpdatedAt());
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

    private SecurityUser requireUser() {
        return SecurityUtils.currentUser().orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
    }

    private BizException param(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null || String.valueOf(value).isBlank() ? 0 : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException e) { throw param("排序值不正确"); }
    }

    private long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null || String.valueOf(value).isBlank() ? 0 : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException e) { return 0; }
    }
}
