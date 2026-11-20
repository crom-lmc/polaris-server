package com.laimengchao.sso.forum.module.experience.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.forum.module.experience.dto.ExperienceRuleRequest;
import com.laimengchao.sso.forum.module.experience.entity.ExperienceRule;
import com.laimengchao.sso.forum.module.experience.mapper.ExperienceRuleMapper;
import com.laimengchao.sso.forum.module.experience.vo.ExperienceRuleVO;
import com.laimengchao.sso.forum.module.forum.mapper.ForumMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/** 经验体系：维护经验获取规则，并在用户产生行为时按规则发放经验（只增不减，与积分相互独立） */
@Service
@RequiredArgsConstructor
public class ExperienceService {

    private static final Set<String> ALLOWED_KEYS = Set.of("POST", "COMMENT");

    private final ExperienceRuleMapper mapper;
    private final ForumMapper forumMapper;

    /** 供发帖/回复调用：按规则给成员增加经验，规则停用或未配置则忽略。bizId 仅用于日志语义 */
    public void grant(long accountId, String ruleKey, String bizId) {
        ExperienceRule rule = mapper.findByKey(ruleKey);
        if (rule == null || !rule.getEnabled() || rule.getExperience() == null || rule.getExperience() <= 0) return;
        forumMapper.increaseExperience(accountId, rule.getExperience());
    }

    public List<ExperienceRuleVO> adminList() {
        return mapper.listAll().stream().map(this::view).toList();
    }

    /** 公开的经验获取途径（启用的规则），供前台展示 */
    public List<ExperienceRuleVO> publicRules() {
        return mapper.listEnabled().stream().map(this::view).toList();
    }

    public ExperienceRuleVO create(ExperienceRuleRequest body) {
        ExperienceRule values = validated(body, null);
        if (ALLOWED_KEYS.contains(values.getRuleKey()) && mapper.findByKey(values.getRuleKey()) != null) {
            throw conflict("该行为标识已配置");
        }
        values.setId(IdWorker.getId());
        mapper.insert(values);
        return view(mapper.findById(values.getId()));
    }

    @Transactional
    public ExperienceRuleVO update(long id, ExperienceRuleRequest body) {
        ExperienceRule current = requireRule(id);
        ExperienceRule values = validated(body, current);
        if (!values.getRuleKey().equals(current.getRuleKey())) {
            ExperienceRule duplicate = mapper.findByKey(values.getRuleKey());
            if (duplicate != null && duplicate.getId() != id) throw conflict("该行为标识已配置");
        }
        values.setId(id);
        if (mapper.update(values) != 1) throw conflict("规则状态已变化，请刷新后重试");
        return view(mapper.findById(id));
    }

    @Transactional
    public void delete(long id) {
        requireRule(id);
        if (mapper.delete(id) != 1) throw conflict("规则状态已变化");
    }

    private ExperienceRule validated(ExperienceRuleRequest body, ExperienceRule current) {
        ExperienceRule values = new ExperienceRule();
        String ruleKey = requiredText(field(body == null ? null : body.getRuleKey(),
                body != null && body.isRuleKeyPresent(), current == null ? null : current.getRuleKey()), "行为标识", 32);
        if (!ALLOWED_KEYS.contains(ruleKey.toUpperCase())) throw param("行为标识仅支持 POST、COMMENT");
        values.setRuleKey(ruleKey.toUpperCase());
        values.setName(requiredText(field(body == null ? null : body.getName(),
                body != null && body.isNamePresent(), current == null ? null : current.getName()), "行为名称", 80));
        values.setExperience(intValue(field(body == null ? null : body.getExperience(),
                body != null && body.isExperiencePresent(), current == null ? null : current.getExperience()), "经验值", 0, 100000));
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

    private ExperienceRule requireRule(long id) {
        ExperienceRule rule = mapper.findById(id);
        if (rule == null) throw new BizException(ErrorCode.NOT_FOUND, "经验规则不存在");
        return rule;
    }

    private ExperienceRuleVO view(ExperienceRule row) {
        ExperienceRuleVO view = new ExperienceRuleVO();
        view.setId(row.getId());
        view.setRuleKey(row.getRuleKey());
        view.setName(row.getName());
        view.setExperience(row.getExperience());
        view.setSortNo(row.getSortNo());
        view.setEnabled(row.getEnabled());
        return view;
    }

    private String requiredText(Object raw, String name, int maxLength) {
        String value = text(raw).trim();
        if (value.isBlank()) throw param(name + "不能为空");
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
