package com.laimengchao.sso.ehr.module.leave.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveTypeRule;
import com.laimengchao.sso.ehr.module.leave.mapper.LeaveTypeRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** 请假类型规则读取与维护。表很小，直接查库，不做缓存以免规则改动后不生效。 */
@Service
@RequiredArgsConstructor
public class LeaveTypeRuleService {

    private final LeaveTypeRuleMapper ruleMapper;

    public List<LeaveTypeRule> list() {
        return ruleMapper.selectList(new LambdaQueryWrapper<LeaveTypeRule>()
                .orderByAsc(LeaveTypeRule::getSortNo));
    }

    /** 规则缺失时按「允许申请、不占额度、0.5~30 天」兜底，保证老字典类型还能用 */
    public LeaveTypeRule get(String type) {
        LeaveTypeRule rule = ruleMapper.selectById(type);
        if (rule != null) {
            return rule;
        }
        LeaveTypeRule fallback = new LeaveTypeRule();
        fallback.setLeaveType(type);
        fallback.setEnabled(1);
        fallback.setQuotaRequired(0);
        fallback.setDefaultQuotaDays(BigDecimal.ZERO);
        fallback.setAttachmentRequired(0);
        fallback.setMinDays(new BigDecimal("0.5"));
        fallback.setMaxDaysPerRequest(new BigDecimal("30.0"));
        fallback.setMinNoticeDays(0);
        fallback.setAllowPastDays(0);
        fallback.setNeedDelegate(0);
        fallback.setSortNo(0);
        return fallback;
    }

    /** 需要占用额度的类型（首次建额度也要用默认额度） */
    public boolean quotaRequired(String type) {
        return Integer.valueOf(1).equals(get(type).getQuotaRequired());
    }

    @Transactional(rollbackFor = Exception.class)
    public LeaveTypeRule save(LeaveTypeRule rule) {
        if (rule.getLeaveType() == null || rule.getLeaveType().isBlank()) {
            throw new BizException(EhrErrorCode.LEAVE_RULE_INVALID, "请假类型不能为空");
        }
        LeaveTypeRule old = ruleMapper.selectById(rule.getLeaveType());
        if (old == null) {
            ruleMapper.insert(rule);
        } else {
            ruleMapper.updateById(rule);
        }
        return ruleMapper.selectById(rule.getLeaveType());
    }
}
