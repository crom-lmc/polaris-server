package com.laimengchao.sso.ehr.module.leave.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveQuota;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveQuotaLog;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveRequest;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveTypeRule;
import com.laimengchao.sso.ehr.module.leave.mapper.LeaveQuotaLogMapper;
import com.laimengchao.sso.ehr.module.leave.mapper.LeaveQuotaMapper;
import com.laimengchao.sso.ehr.module.leave.vo.LeaveQuotaVO;
import com.laimengchao.sso.ehr.module.leave.vo.QuotaLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongUnaryOperator;

/**
 * 假期额度闭环：冻结（提交）/ 扣减（通过）/ 释放（驳回、撤回、销假差额）/ 调整（审批）。
 * <p>
 * 所有变更都是「先写幂等流水（biz_key 唯一）→ 再做带余额条件的原子 UPDATE」，
 * 流水写入失败说明回调已处理过，直接跳过；UPDATE 影响 0 行说明余额不足，抛业务错误整体回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveQuotaService {

    public static final String ACTION_FREEZE = "FREEZE";
    public static final String ACTION_DEDUCT = "DEDUCT";
    public static final String ACTION_RELEASE = "RELEASE";
    public static final String ACTION_ADJUST = "ADJUST";
    public static final String ACTION_SETTLE = "SETTLE";

    private final LeaveQuotaMapper quotaMapper;
    private final LeaveQuotaLogMapper logMapper;
    private final LeaveTypeRuleService ruleService;
    private final AnnualLeaveService annualLeaveService;

    public LeaveQuota getById(Long quotaId) {
        return quotaMapper.selectById(quotaId);
    }

    /** 按「员工 + 类型 + 周期起始日」取额度；year 只作为周期换算参数，不再参与查询 */
    public LeaveQuota find(Long employeeId, String leaveType, int year) {
        return quotaMapper.selectOne(new LambdaQueryWrapper<LeaveQuota>()
                .eq(LeaveQuota::getEmployeeId, employeeId)
                .eq(LeaveQuota::getLeaveType, leaveType)
                .eq(LeaveQuota::getPeriodStart, LocalDate.of(year, 1, 1))
                .last("limit 1"));
    }

    /** 取额度行；不占额度的类型返回 null。缺失时按规则默认额度自动建行（并发靠唯一键兜底）。 */
    public LeaveQuota getOrCreate(Long employeeId, String leaveType, int year) {
        LeaveQuota q = find(employeeId, leaveType, year);
        if (q != null) {
            return q;
        }
        // 年假额度统一由策略生成（含工龄档位 / 新入职折算 / 上年度结转），避免被默认额度抢先建行
        if (AnnualLeaveService.LEAVE_TYPE_ANNUAL.equalsIgnoreCase(leaveType)) {
            return annualLeaveService.generateYear(employeeId, year);
        }
        LeaveTypeRule rule = ruleService.get(leaveType);
        if (!Integer.valueOf(1).equals(rule.getQuotaRequired())) {
            return null;
        }
        LeaveQuota n = new LeaveQuota();
        n.setEmployeeId(employeeId);
        n.setLeaveType(leaveType);
        n.setYear(year);
        n.setPeriodStart(LocalDate.of(year, 1, 1));
        n.setPeriodEnd(LocalDate.of(year, 12, 31));
        n.setPeriodNo(1);
        n.setTotalDays(nvl(rule.getDefaultQuotaDays()));
        n.setUsedDays(BigDecimal.ZERO);
        n.setFrozenDays(BigDecimal.ZERO);
        n.setCarryoverIn(BigDecimal.ZERO);
        n.setCarryoverOut(BigDecimal.ZERO);
        n.setSource("AUTO");
        n.setCalcDetail("按请假类型默认额度生成");
        n.setVersion(0);
        try {
            quotaMapper.insert(n);
            return n;
        } catch (DuplicateKeyException e) {
            // 并发下另一线程已建行，重新读取即可
            return find(employeeId, leaveType, year);
        }
    }

    public BigDecimal available(LeaveQuota q) {
        if (q == null) {
            return BigDecimal.ZERO;
        }
        return nvl(q.getTotalDays()).subtract(nvl(q.getUsedDays())).subtract(nvl(q.getFrozenDays()));
    }

    public LeaveQuotaVO toVO(LeaveQuota q) {
        LeaveQuotaVO vo = new LeaveQuotaVO();
        vo.setId(q.getId());
        vo.setEmployeeId(q.getEmployeeId());
        vo.setLeaveType(q.getLeaveType());
        vo.setYear(q.getYear());
        vo.setPeriodStart(q.getPeriodStart() == null ? null : q.getPeriodStart().toString());
        vo.setPeriodEnd(q.getPeriodEnd() == null ? null : q.getPeriodEnd().toString());
        vo.setTotalDays(nvl(q.getTotalDays()));
        vo.setUsedDays(nvl(q.getUsedDays()));
        vo.setFrozenDays(nvl(q.getFrozenDays()));
        vo.setAvailableDays(available(q));
        vo.setCarryoverIn(nvl(q.getCarryoverIn()));
        vo.setCarryoverOut(nvl(q.getCarryoverOut()));
        vo.setCarryoverExpireAt(q.getCarryoverExpireAt() == null ? null : q.getCarryoverExpireAt().toString());
        vo.setSource(q.getSource());
        vo.setCalcDetail(nvl(q.getCalcDetail()));
        return vo;
    }

    /** 某员工某年度的全部占额度类型（缺行的按默认额度补建） */
    @Transactional(rollbackFor = Exception.class)
    public List<LeaveQuotaVO> listForEmployee(Long employeeId, int year) {
        List<LeaveQuotaVO> out = new ArrayList<>();
        for (LeaveTypeRule rule : ruleService.list()) {
            if (!Integer.valueOf(1).equals(rule.getQuotaRequired())) {
                continue;
            }
            LeaveQuota q = getOrCreate(employeeId, rule.getLeaveType(), year);
            if (q != null) {
                out.add(toVO(q));
            }
        }
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public void initForEmployee(Long employeeId, int year) {
        for (LeaveTypeRule rule : ruleService.list()) {
            if (Integer.valueOf(1).equals(rule.getQuotaRequired())) {
                getOrCreate(employeeId, rule.getLeaveType(), year);
            }
        }
    }

    /** 提交请假时冻结额度；额度不足抛出业务错误，让请假单与冻结一起回滚 */
    @Transactional(rollbackFor = Exception.class)
    public void freezeForLeave(LeaveRequest leave, Long operatorId) {
        if (!ruleService.quotaRequired(leave.getLeaveType())) {
            return;
        }
        LeaveQuota q = getOrCreate(leave.getEmployeeId(), leave.getLeaveType(), leave.getStartDate().getYear());
        if (q == null) {
            throw new BizException(EhrErrorCode.LEAVE_QUOTA_NOT_ENOUGH, "请假类型「" + leave.getLeaveType() + "」没有可用额度");
        }
        change(q, ACTION_FREEZE, leave.getDays(), leave.getId(),
                "LEAVE:" + leave.getId() + ":FREEZE", operatorId, "提交请假冻结",
                id -> quotaMapper.freeze(id, leave.getDays()), "可用 " + available(q) + " 天");
    }

    /** 审批通过：冻结转已用 */
    @Transactional(rollbackFor = Exception.class)
    public void consumeForLeave(LeaveRequest leave, Long operatorId) {
        if (!ruleService.quotaRequired(leave.getLeaveType())) {
            return;
        }
        LeaveQuota q = find(leave.getEmployeeId(), leave.getLeaveType(), leave.getStartDate().getYear());
        if (q == null) {
            log.error("请假审批通过但额度行不存在，跳过扣减 leaveId={} employee={} type={}",
                    leave.getId(), leave.getEmployeeId(), leave.getLeaveType());
            return;
        }
        change(q, ACTION_DEDUCT, leave.getDays(), leave.getId(),
                "LEAVE:" + leave.getId() + ":DEDUCT", operatorId, "审批通过扣减",
                id -> quotaMapper.consume(id, leave.getDays()), "冻结转已用");
    }

    /** 驳回 / 撤回 / 终止：释放冻结 */
    @Transactional(rollbackFor = Exception.class)
    public void releaseForLeave(LeaveRequest leave, Long operatorId) {
        if (!ruleService.quotaRequired(leave.getLeaveType())) {
            return;
        }
        LeaveQuota q = find(leave.getEmployeeId(), leave.getLeaveType(), leave.getStartDate().getYear());
        if (q == null) {
            return;
        }
        change(q, ACTION_RELEASE, leave.getDays(), leave.getId(),
                "LEAVE:" + leave.getId() + ":RELEASE", operatorId, "驳回/撤回释放冻结",
                id -> quotaMapper.release(id, leave.getDays()), "释放冻结");
    }

    /** 销假提前返岗：退回多请的天数（写 RELEASE 流水，幂等键按请假单唯一） */
    @Transactional(rollbackFor = Exception.class)
    public void returnDays(LeaveRequest leave, BigDecimal returnDays, Long operatorId) {
        if (!ruleService.quotaRequired(leave.getLeaveType()) || returnDays == null
                || returnDays.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        LeaveQuota q = find(leave.getEmployeeId(), leave.getLeaveType(), leave.getStartDate().getYear());
        if (q == null) {
            return;
        }
        change(q, ACTION_RELEASE, returnDays, leave.getId(),
                "LEAVE:" + leave.getId() + ":EARLY_RETURN", operatorId, "销假退回额度",
                id -> quotaMapper.release(id, returnDays), "退回 " + returnDays + " 天");
    }

    /** 额度调整审批通过：delta 可为负，调整后总额度不得低于已用 + 冻结 */
    @Transactional(rollbackFor = Exception.class)
    public void adjust(Long quotaId, BigDecimal delta, Long operatorId, String remark, String bizKey) {
        LeaveQuota q = quotaMapper.selectById(quotaId);
        if (q == null) {
            throw new BizException(EhrErrorCode.LEAVE_QUOTA_NOT_FOUND, "额度记录不存在");
        }
        change(q, ACTION_ADJUST, delta, null, bizKey, operatorId, remark,
                id -> quotaMapper.adjust(id, delta), "调整额度");
    }

    public List<QuotaLogVO> logs(Long quotaId) {
        return logMapper.selectList(new LambdaQueryWrapper<LeaveQuotaLog>()
                        .eq(LeaveQuotaLog::getQuotaId, quotaId)
                        .orderByDesc(LeaveQuotaLog::getCreatedAt))
                .stream().map(this::toLogVO).toList();
    }

    private void change(LeaveQuota q, String action, BigDecimal days, Long leaveId, String bizKey,
                        Long operatorId, String remark, LongUnaryOperator op, String insufficientHint) {
        BigDecimal before = available(q);
        LeaveQuotaLog lg = new LeaveQuotaLog();
        lg.setId(IdWorker.getId());
        lg.setQuotaId(q.getId());
        lg.setEmployeeId(q.getEmployeeId());
        lg.setLeaveId(leaveId);
        lg.setBizKey(bizKey);
        lg.setAction(action);
        lg.setDays(days);
        lg.setBeforeAvailable(before);
        lg.setAfterAvailable(after(action, before, days));
        lg.setOperatorId(operatorId);
        lg.setRemark(remark);
        // 幂等闸门：流水写不进去说明这单已处理过，直接返回
        if (logMapper.insertIgnore(lg) == 0) {
            log.info("额度变更已处理过，跳过 bizKey={}", bizKey);
            return;
        }
        long n = op.applyAsLong(q.getId());
        if (n == 0) {
            throw new BizException(EhrErrorCode.LEAVE_QUOTA_NOT_ENOUGH,
                    "假期余额不足（" + insufficientHint + "），无法完成本次操作");
        }
    }

    private BigDecimal after(String action, BigDecimal before, BigDecimal days) {
        if (ACTION_FREEZE.equals(action) || ACTION_DEDUCT.equals(action)) {
            return before.subtract(days);
        }
        return before.add(days);
    }

    private QuotaLogVO toLogVO(LeaveQuotaLog l) {
        QuotaLogVO vo = new QuotaLogVO();
        vo.setId(l.getId());
        vo.setLeaveId(l.getLeaveId());
        vo.setAction(l.getAction());
        vo.setActionName(actionName(l.getAction()));
        vo.setDays(l.getDays());
        vo.setBeforeAvailable(l.getBeforeAvailable());
        vo.setAfterAvailable(l.getAfterAvailable());
        vo.setRemark(l.getRemark());
        vo.setCreatedAt(l.getCreatedAt() == null ? null : l.getCreatedAt().toString().replace('T', ' '));
        return vo;
    }

    private String actionName(String action) {
        if (ACTION_FREEZE.equals(action)) return "冻结";
        if (ACTION_DEDUCT.equals(action)) return "扣减";
        if (ACTION_RELEASE.equals(action)) return "释放";
        if (ACTION_ADJUST.equals(action)) return "调整";
        if (ACTION_SETTLE.equals(action)) return "结算";
        return action;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String nvl(String v) {
        return v == null ? "" : v;
    }
}
