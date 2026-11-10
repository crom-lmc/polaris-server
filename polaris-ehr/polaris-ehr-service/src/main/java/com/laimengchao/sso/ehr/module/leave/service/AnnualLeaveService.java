package com.laimengchao.sso.ehr.module.leave.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.ehr.module.employee.constant.EmployeeStatus;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.employee.service.SeniorityService;
import com.laimengchao.sso.ehr.module.leave.entity.AnnualLeavePolicy;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveQuota;
import com.laimengchao.sso.ehr.module.leave.mapper.AnnualLeavePolicyMapper;
import com.laimengchao.sso.ehr.module.leave.mapper.LeaveQuotaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 法定年假（P4-4）：策略维护、按累计工龄生成年度额度、上年度结转、跨档补差。
 * <p>
 * 额度周期统一按自然年：period_start=year-01-01，period_end=year-12-31；
 * 唯一键与 LeaveQuotaService 查询都以 period_start 为准。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnualLeaveService {

    public static final String LEAVE_TYPE_ANNUAL = "ANNUAL";
    private static final String SOURCE_AUTO = "AUTO";
    private static final int CALC_DETAIL_MAX = 500;

    private final AnnualLeavePolicyMapper policyMapper;
    private final LeaveQuotaMapper quotaMapper;
    private final EmployeeMapper employeeMapper;

    // ---------------------------------------------------------------- 策略

    public List<AnnualLeavePolicy> listPolicies() {
        return policyMapper.selectList(new LambdaQueryWrapper<AnnualLeavePolicy>()
                .orderByDesc(AnnualLeavePolicy::getEnabled)
                .orderByAsc(AnnualLeavePolicy::getId));
    }

    /**
     * 新增 / 更新策略。enabled=1 时自动停用其他策略，保证全局只有一条启用。
     * 更新时未传字段保持原值（updateById 跳过 null）。
     */
    @Transactional(rollbackFor = Exception.class)
    public AnnualLeavePolicy savePolicy(AnnualLeavePolicy policy) {
        if (policy == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "策略参数不能为空");
        }
        if (policy.getId() == null) {
            if (policy.getName() == null || policy.getName().isBlank()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "策略名称必填");
            }
            fillDefaults(policy);
            policyMapper.insert(policy);
        } else {
            if (policyMapper.selectById(policy.getId()) == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "年假策略不存在");
            }
            policyMapper.updateById(policy);
        }
        if (Integer.valueOf(1).equals(policy.getEnabled())) {
            disableOthers(policy.getId());
        }
        return policyMapper.selectById(policy.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public AnnualLeavePolicy enablePolicy(Long id) {
        AnnualLeavePolicy p = policyMapper.selectById(id);
        if (p == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "年假策略不存在");
        }
        disableOthers(id);
        p.setEnabled(1);
        policyMapper.updateById(p);
        return p;
    }

    private void disableOthers(Long keepId) {
        policyMapper.update(null, new LambdaUpdateWrapper<AnnualLeavePolicy>()
                .ne(AnnualLeavePolicy::getId, keepId)
                .eq(AnnualLeavePolicy::getEnabled, 1)
                .set(AnnualLeavePolicy::getEnabled, 0));
    }

    private void fillDefaults(AnnualLeavePolicy p) {
        if (p.getEnabled() == null) {
            p.setEnabled(0);
        }
        if (p.getTier1Years() == null) {
            p.setTier1Years(1);
        }
        if (p.getTier1Days() == null) {
            p.setTier1Days(new BigDecimal("5"));
        }
        if (p.getTier2Years() == null) {
            p.setTier2Years(10);
        }
        if (p.getTier2Days() == null) {
            p.setTier2Days(new BigDecimal("10"));
        }
        if (p.getTier3Years() == null) {
            p.setTier3Years(20);
        }
        if (p.getTier3Days() == null) {
            p.setTier3Days(new BigDecimal("15"));
        }
        if (p.getCarryoverEnabled() == null) {
            p.setCarryoverEnabled(1);
        }
        if (p.getCarryoverMaxDays() == null) {
            p.setCarryoverMaxDays(new BigDecimal("5"));
        }
        if (p.getCarryoverExpireMonth() == null) {
            p.setCarryoverExpireMonth(3);
        }
        if (p.getEffectiveFrom() == null) {
            p.setEffectiveFrom(LocalDate.now());
        }
        if (p.getRemark() == null) {
            p.setRemark("");
        }
    }

    /** 取启用中的策略；未配置时使用法定默认档位 */
    public AnnualLeavePolicy activePolicy() {
        AnnualLeavePolicy p = policyMapper.selectOne(new LambdaQueryWrapper<AnnualLeavePolicy>()
                .eq(AnnualLeavePolicy::getEnabled, 1)
                .orderByAsc(AnnualLeavePolicy::getId)
                .last("limit 1"));
        return p == null ? defaultPolicy() : p;
    }

    private AnnualLeavePolicy defaultPolicy() {
        AnnualLeavePolicy p = new AnnualLeavePolicy();
        p.setName("法定年假（默认）");
        p.setEnabled(1);
        p.setTier1Years(1);
        p.setTier1Days(new BigDecimal("5"));
        p.setTier2Years(10);
        p.setTier2Days(new BigDecimal("10"));
        p.setTier3Years(20);
        p.setTier3Days(new BigDecimal("15"));
        p.setCarryoverEnabled(1);
        p.setCarryoverMaxDays(new BigDecimal("5"));
        p.setCarryoverExpireMonth(3);
        return p;
    }

    // ---------------------------------------------------------------- 年度额度

    /**
     * 生成某员工某自然年的年假额度：初始档位按「截至当年 1 月 1 日的累计工龄」确定；
     * 当年新入职按入职日至年底自然日/365 折算；再叠加符合规则的上年度结转。
     */
    @Transactional(rollbackFor = Exception.class)
    public LeaveQuota generateYear(Long employeeId, int year) {
        Employee emp = requireEmployee(employeeId);
        LeaveQuota exists = findQuota(employeeId, year);
        if (exists != null) {
            return exists;
        }
        AnnualLeavePolicy policy = activePolicy();
        LocalDate periodStart = LocalDate.of(year, 1, 1);
        LocalDate firstWork = firstWorkDate(emp);
        BigDecimal yearsAtJan1 = SeniorityService.yearsBetween(firstWork, periodStart);
        BigDecimal base = tierDays(yearsAtJan1, policy);

        LocalDate hire = emp.getHireDate() != null ? emp.getHireDate() : emp.getSeniorityStartDate();
        boolean newHire = hire != null && hire.getYear() == year;
        BigDecimal baseDays = newHire ? prorate(hire, year, base) : base.setScale(1, RoundingMode.DOWN);

        BigDecimal carry = carryover(employeeId, year, policy);
        BigDecimal total = baseDays.add(carry).setScale(1, RoundingMode.DOWN);

        StringBuilder detail = new StringBuilder()
                .append("截至").append(year).append("-01-01累计工龄")
                .append(yearsAtJan1.toPlainString()).append("年，档位")
                .append(base.toPlainString()).append("天");
        if (newHire) {
            detail.append("；").append(hire).append("入职，按自然日折算")
                    .append(baseDays.toPlainString()).append("天");
        }
        if (carry.compareTo(BigDecimal.ZERO) > 0) {
            detail.append("；结转上年度").append(carry.toPlainString()).append("天");
        }

        LeaveQuota q = new LeaveQuota();
        q.setEmployeeId(employeeId);
        q.setLeaveType(LEAVE_TYPE_ANNUAL);
        q.setYear(year);
        q.setPeriodStart(periodStart);
        q.setPeriodEnd(LocalDate.of(year, 12, 31));
        q.setPeriodNo(1);
        q.setTotalDays(total);
        q.setUsedDays(BigDecimal.ZERO);
        q.setFrozenDays(BigDecimal.ZERO);
        q.setCarryoverIn(carry);
        q.setCarryoverOut(carry);
        q.setCarryoverExpireAt(carry.compareTo(BigDecimal.ZERO) > 0
                ? LocalDate.of(year, expireMonth(policy), 1) : null);
        q.setSource(SOURCE_AUTO);
        q.setCalcDetail(truncate(detail.toString()));
        q.setVersion(0);
        try {
            quotaMapper.insert(q);
        } catch (DuplicateKeyException e) {
            // 并发下（每日任务 / 请假时补建）另一线程已建行，直接返回已有额度
            return findQuota(employeeId, year);
        }
        log.info("生成年假额度 employeeId={} year={} total={} carry={}", employeeId, year, total, carry);
        return q;
    }

    /** 上年度结转：min(上年度可用, 策略上限)，无上年度额度则为 0 */
    private BigDecimal carryover(Long employeeId, int year, AnnualLeavePolicy policy) {
        if (!Integer.valueOf(1).equals(policy.getCarryoverEnabled())) {
            return BigDecimal.ZERO;
        }
        LeaveQuota prev = findQuota(employeeId, year - 1);
        if (prev == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal available = nvl(prev.getTotalDays())
                .subtract(nvl(prev.getUsedDays()))
                .subtract(nvl(prev.getFrozenDays()));
        if (available.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal max = nvl(policy.getCarryoverMaxDays());
        return available.min(max).setScale(1, RoundingMode.DOWN);
    }

    /**
     * 跨档补差：仅处理当年内跨过 10 / 20 周年（首次满 1 年不重复补，首年已按入职折算）。
     * source=MANUAL 的额度不动，只调整自动生成的额度。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean recompute(Long employeeId, LocalDate date) {
        if (date == null) {
            return false;
        }
        Employee emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            return false;
        }
        LocalDate firstWork = firstWorkDate(emp);
        if (firstWork == null) {
            return false;
        }
        AnnualLeavePolicy policy = activePolicy();
        BigDecimal before = SeniorityService.yearsBetween(firstWork, date.minusDays(1));
        BigDecimal after = SeniorityService.yearsBetween(firstWork, date);
        BigDecimal t2 = BigDecimal.valueOf(tierYears(policy.getTier2Years(), 10));
        BigDecimal t3 = BigDecimal.valueOf(tierYears(policy.getTier3Years(), 20));

        BigDecimal delta;
        if (before.compareTo(t3) < 0 && after.compareTo(t3) >= 0) {
            delta = nvl(policy.getTier3Days()).subtract(nvl(policy.getTier2Days()));
        } else if (before.compareTo(t2) < 0 && after.compareTo(t2) >= 0) {
            delta = nvl(policy.getTier2Days()).subtract(nvl(policy.getTier1Days()));
        } else {
            return false;
        }
        if (delta.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        LeaveQuota q = findQuota(employeeId, date.getYear());
        if (q == null || !SOURCE_AUTO.equalsIgnoreCase(q.getSource())) {
            return false;
        }
        String marker = "跨档补差" + date;
        if (q.getCalcDetail() != null && q.getCalcDetail().contains(marker)) {
            return false;
        }
        String detail = appendDetail(q.getCalcDetail(),
                marker + " +" + delta.stripTrailingZeros().toPlainString() + "天");
        int n = quotaMapper.increaseTotal(q.getId(), delta, detail);
        if (n > 0) {
            log.info("年假跨档补差 employeeId={} date={} delta={}", employeeId, date, delta);
        }
        return n > 0;
    }

    /** 手动生成全员当年额度（联调用），返回本次新建数量 */
    @Transactional(rollbackFor = Exception.class)
    public int generateAll(int year) {
        int created = 0;
        for (Employee e : workingEmployees()) {
            if (findQuota(e.getId(), year) == null) {
                generateYear(e.getId(), year);
                created++;
            }
        }
        log.info("手动生成 {} 年年假额度，新建 {} 条", year, created);
        return created;
    }

    /** 每日 02:30：在职员工缺失额度则补建，并处理当天跨档补差（调度入口见 schedule 包的 AnnualLeaveRefreshJob） */
    public void dailyRefresh() {
        int year = LocalDate.now().getYear();
        LocalDate today = LocalDate.now();
        int created = 0;
        int adjusted = 0;
        for (Employee e : workingEmployees()) {
            try {
                if (findQuota(e.getId(), year) == null) {
                    generateYear(e.getId(), year);
                    created++;
                }
                if (recompute(e.getId(), today)) {
                    adjusted++;
                }
            } catch (Exception ex) {
                log.warn("年假额度刷新失败 employeeId={} year={}：{}", e.getId(), year, ex.getMessage());
            }
        }
        log.info("每日年假刷新完成 year={} 新建={} 跨档补差={}", year, created, adjusted);
    }

    // ---------------------------------------------------------------- 工具

    private List<Employee> workingEmployees() {
        return employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .in(Employee::getStatus, EmployeeStatus.PROBATION.getCode(), EmployeeStatus.ACTIVE.getCode()));
    }

    private LeaveQuota findQuota(Long employeeId, int year) {
        return quotaMapper.selectOne(new LambdaQueryWrapper<LeaveQuota>()
                .eq(LeaveQuota::getEmployeeId, employeeId)
                .eq(LeaveQuota::getLeaveType, LEAVE_TYPE_ANNUAL)
                .eq(LeaveQuota::getPeriodStart, LocalDate.of(year, 1, 1))
                .last("limit 1"));
    }

    private Employee requireEmployee(Long employeeId) {
        Employee emp = employeeId == null ? null : employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        return emp;
    }

    private LocalDate firstWorkDate(Employee e) {
        if (e.getFirstWorkDate() != null) {
            return e.getFirstWorkDate();
        }
        return e.getSeniorityStartDate() != null ? e.getSeniorityStartDate() : e.getHireDate();
    }

    /** 档位天数：默认 1 年 5 天 / 10 年 10 天 / 20 年 15 天 */
    private BigDecimal tierDays(BigDecimal years, AnnualLeavePolicy p) {
        if (years.compareTo(BigDecimal.valueOf(tierYears(p.getTier3Years(), 20))) >= 0) {
            return nvl(p.getTier3Days());
        }
        if (years.compareTo(BigDecimal.valueOf(tierYears(p.getTier2Years(), 10))) >= 0) {
            return nvl(p.getTier2Days());
        }
        return nvl(p.getTier1Days());
    }

    /** 当年新入职：按入职日到年底自然日 / 365 折算，最少 0 天，保留 1 位 */
    private BigDecimal prorate(LocalDate hire, int year, BigDecimal baseDays) {
        LocalDate end = LocalDate.of(year, 12, 31);
        if (hire.isAfter(end)) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.DOWN);
        }
        long days = ChronoUnit.DAYS.between(hire, end) + 1;
        return baseDays.multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(365), 1, RoundingMode.DOWN)
                .max(BigDecimal.ZERO.setScale(1, RoundingMode.DOWN));
    }

    private int tierYears(Integer v, int def) {
        return v == null ? def : v;
    }

    private int expireMonth(AnnualLeavePolicy p) {
        int m = p.getCarryoverExpireMonth() == null ? 3 : p.getCarryoverExpireMonth();
        return Math.min(12, Math.max(1, m));
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String appendDetail(String old, String add) {
        String s = old == null || old.isBlank() ? add : old + "；" + add;
        return truncate(s);
    }

    private String truncate(String s) {
        return s != null && s.length() > CALC_DETAIL_MAX ? s.substring(0, CALC_DETAIL_MAX) : s;
    }
}
