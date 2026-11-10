package com.laimengchao.sso.ehr.module.leave.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.leave.vo.AnnualLeaveLedgerVO;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import com.laimengchao.sso.ehr.module.employee.constant.EmployeeStatus;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.leave.entity.AnnualLeavePolicy;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveQuota;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveQuotaLog;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveRequest;
import com.laimengchao.sso.ehr.module.leave.mapper.AnnualLeavePolicyMapper;
import com.laimengchao.sso.ehr.module.leave.mapper.LeaveCarryoverWaiverMapper;
import com.laimengchao.sso.ehr.module.leave.mapper.LeaveMapper;
import com.laimengchao.sso.ehr.module.leave.mapper.LeaveQuotaLogMapper;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 法定年假（P4-4）：策略维护、年度额度生成、结转、每日重算。
 * <p>
 * 折算规则（统一公式，覆盖截图 10 个例子）：
 * <ul>
 *   <li>资格：累计工龄满 1 年（firstWorkDate + 1 年）起产生年假；1 月 1 日生成时按「预计今年内满 1 年」分段折算</li>
 *   <li>区间：from = max(1/1, 入职日, 资格日)，to = min(12/31, 离职日)；年中离职按在册天数折算</li>
 *   <li>档位：&lt;10 年 5 天 / [10,20) 10 天 / ≥20 年 15 天；年内跨档按 firstWorkDate+N 年分界点分段按天折算</li>
 *   <li>精度：Σ(分段天数 × 档位) / 365，向下取整到 0.5 天</li>
 *   <li>条例第 4 条：当年事假 ≥20 天，或病假 ≥44/66/87 天（按工龄档 2/3/4 个月 × 21.75 向上取整），当年年假为 0</li>
 * </ul>
 * 结转：无上限，结转的是「上年当年产生部分」的剩余（消耗优先扣结转）；
 * 结转额度在当年 12-31 失效，12 月仍未休完由调度每日待办提醒，可签署自动放弃。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnualLeaveService {

    public static final String LEAVE_TYPE_ANNUAL = "ANNUAL";
    private static final String LEAVE_TYPE_PERSONAL = "PERSONAL";
    private static final String LEAVE_TYPE_SICK = "SICK";
    private static final String SOURCE_AUTO = "AUTO";
    private static final int CALC_DETAIL_MAX = 500;
    /** 条例第 4 条：事假 20 天 */
    private static final BigDecimal PERSONAL_EXCLUDE_DAYS = new BigDecimal("20");
    /** 病假月数折工作日：21.75 天/月，2/3/4 个月向上取整 = 44/66/87 */
    private static final BigDecimal WORKDAYS_PER_MONTH = new BigDecimal("21.75");

    private final AnnualLeavePolicyMapper policyMapper;
    private final LeaveQuotaMapper quotaMapper;
    private final LeaveQuotaLogMapper quotaLogMapper;
    private final LeaveCarryoverWaiverMapper waiverMapper;
    private final LeaveMapper leaveMapper;
    private final EmployeeMapper employeeMapper;
    private final OrgNodeMapper orgMapper;

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
            p.setCarryoverExpireMonth(12);
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
        p.setCarryoverExpireMonth(12);
        return p;
    }

    // ---------------------------------------------------------------- 年度额度

    /**
     * 生成某员工某自然年的年假额度：按统一折算公式计算当年产生额度，再全额结转上年剩余（无上限）。
     * 同时给上一年额度写「结转剩余年底作废」的 SETTLE 流水（幂等）。
     */
    @Transactional(rollbackFor = Exception.class)
    public LeaveQuota generateYear(Long employeeId, int year) {
        Employee emp = requireEmployee(employeeId);
        LeaveQuota exists = findQuota(employeeId, year);
        if (exists != null) {
            return exists;
        }
        AnnualLeavePolicy policy = activePolicy();
        CalcResult calc = calcAnnualDays(emp, year, policy);

        BigDecimal carry = carryover(employeeId, year, policy);
        BigDecimal total = calc.days.add(carry);

        StringBuilder detail = new StringBuilder(calc.detail);
        if (carry.compareTo(BigDecimal.ZERO) > 0) {
            detail.append("；结转上年度").append(carry.stripTrailingZeros().toPlainString()).append("天");
        }

        LeaveQuota q = new LeaveQuota();
        q.setEmployeeId(employeeId);
        q.setLeaveType(LEAVE_TYPE_ANNUAL);
        q.setYear(year);
        q.setPeriodStart(LocalDate.of(year, 1, 1));
        q.setPeriodEnd(LocalDate.of(year, 12, 31));
        q.setPeriodNo(1);
        q.setTotalDays(total);
        q.setUsedDays(BigDecimal.ZERO);
        q.setFrozenDays(BigDecimal.ZERO);
        q.setCarryoverIn(carry);
        q.setCarryoverOut(carry);
        q.setCarryoverUsed(BigDecimal.ZERO);
        q.setCarryoverExpireAt(carry.compareTo(BigDecimal.ZERO) > 0 ? LocalDate.of(year, 12, 31) : null);
        q.setSource(SOURCE_AUTO);
        q.setCalcDetail(truncate(detail.toString()));
        q.setVersion(0);
        try {
            quotaMapper.insert(q);
        } catch (DuplicateKeyException e) {
            // 并发下（每日任务 / 请假时补建）另一线程已建行，直接返回已有额度
            return findQuota(employeeId, year);
        }
        settlePrevYearCarryover(emp, year, carry);
        log.info("生成年假额度 employeeId={} year={} total={} carry={}", employeeId, year, total, carry);
        return q;
    }

    /**
     * 上年度结转（全额、无上限）：结转的是「上年当年产生部分」的剩余。
     * 消耗优先扣结转，故 产生部分剩余 = (total - carryoverIn) - max(0, used+frozen - carryoverIn)。
     */
    private BigDecimal carryover(Long employeeId, int year, AnnualLeavePolicy policy) {
        if (!Integer.valueOf(1).equals(policy.getCarryoverEnabled())) {
            return BigDecimal.ZERO;
        }
        LeaveQuota prev = findQuota(employeeId, year - 1);
        if (prev == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal generated = nvl(prev.getTotalDays()).subtract(nvl(prev.getCarryoverIn()));
        BigDecimal generatedConsumed = nvl(prev.getUsedDays()).add(nvl(prev.getFrozenDays()))
                .subtract(nvl(prev.getCarryoverIn())).max(BigDecimal.ZERO);
        return generated.subtract(generatedConsumed).max(BigDecimal.ZERO);
    }

    /**
     * 上一年结转剩余在年底作废：1/1 生成新年额度时给旧年额度写 SETTLE 流水（幂等 bizKey），
     * 并把旧年 carryoverOut 修正为实际结转量。
     */
    private void settlePrevYearCarryover(Employee emp, int year, BigDecimal carry) {
        LeaveQuota prev = findQuota(emp.getId(), year - 1);
        if (prev == null) {
            return;
        }
        // 回写上一年实际结转量
        if (carry.compareTo(nvl(prev.getCarryoverOut())) != 0) {
            quotaMapper.updateCarryoverOut(prev.getId(), carry);
        }
        BigDecimal remaining = nvl(prev.getCarryoverIn()).subtract(nvl(prev.getCarryoverUsed()));
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        LeaveQuotaLog lg = new LeaveQuotaLog();
        lg.setId(IdWorker.getId());
        lg.setQuotaId(prev.getId());
        lg.setEmployeeId(emp.getId());
        lg.setBizKey("EXPIRE:" + emp.getId() + ":" + (year - 1));
        lg.setAction("SETTLE");
        lg.setDays(remaining);
        lg.setBeforeAvailable(available(prev));
        lg.setAfterAvailable(available(prev));
        lg.setRemark((year - 1) + " 年结转剩余 " + remaining.stripTrailingZeros().toPlainString()
                + " 天于 " + (year - 1) + "-12-31 到期作废");
        quotaLogMapper.insertIgnore(lg);
    }

    /**
     * 统一折算公式：返回当年产生年假天数与计算说明。
     */
    public CalcResult calcAnnualDays(Employee emp, int year, AnnualLeavePolicy policy) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        LocalDate firstWork = firstWorkDate(emp);
        CalcResult r = new CalcResult();
        if (firstWork == null) {
            r.days = BigDecimal.ZERO;
            r.detail = "缺少首次工作日期，无法折算";
            return r;
        }
        LocalDate eligibleFrom = firstWork.plusYears(tierYears(policy.getTier1Years(), 1));
        LocalDate from = yearStart;
        if (emp.getHireDate() != null && emp.getHireDate().isAfter(from)) {
            from = emp.getHireDate();
        }
        if (eligibleFrom.isAfter(from)) {
            from = eligibleFrom;
        }
        LocalDate to = yearEnd;
        if (emp.getResignDate() != null && emp.getResignDate().isBefore(to)) {
            to = emp.getResignDate();
        }
        StringBuilder detail = new StringBuilder("首次工作").append(firstWork);
        if (from.isAfter(to)) {
            r.days = BigDecimal.ZERO;
            r.detail = truncate(detail.append("，").append(year).append(" 年无在册资格区间").toString());
            return r;
        }
        if (emp.getHireDate() != null && emp.getHireDate().isAfter(yearStart)) {
            detail.append("，").append(emp.getHireDate()).append("入职");
        }
        if (emp.getResignDate() != null && emp.getResignDate().isBefore(yearEnd)) {
            detail.append("，").append(emp.getResignDate()).append("离职");
        }
        detail.append("，折算区间").append(from).append("~").append(to);

        // 条例第 4 条：当年事假/病假超限，当年不享受年假
        String exclude = exclusionReason(emp, year, firstWork, policy);
        if (exclude != null) {
            r.days = BigDecimal.ZERO;
            r.detail = truncate(detail.append("；").append(exclude).toString());
            return r;
        }

        // 跨档分界点：首次工作 + 10 / 20 年
        LocalDate b2 = firstWork.plusYears(tierYears(policy.getTier2Years(), 10));
        LocalDate b3 = firstWork.plusYears(tierYears(policy.getTier3Years(), 20));
        BigDecimal d1 = nvl(policy.getTier1Days());
        BigDecimal d2 = nvl(policy.getTier2Days());
        BigDecimal d3 = nvl(policy.getTier3Days());

        BigDecimal sum = BigDecimal.ZERO;
        LocalDate segStart = from;
        while (!segStart.isAfter(to)) {
            LocalDate segEnd = to;
            BigDecimal rate;
            if (segStart.isBefore(b2)) {
                rate = d1;
                if (b2.minusDays(1).isBefore(segEnd)) {
                    segEnd = b2.minusDays(1);
                }
            } else if (segStart.isBefore(b3)) {
                rate = d2;
                if (b3.minusDays(1).isBefore(segEnd)) {
                    segEnd = b3.minusDays(1);
                }
            } else {
                rate = d3;
            }
            long segDays = ChronoUnit.DAYS.between(segStart, segEnd) + 1;
            sum = sum.add(rate.multiply(BigDecimal.valueOf(segDays)));
            if (segEnd.equals(to)) {
                break;
            }
            detail.append("；").append(segStart).append("~").append(segEnd)
                    .append(" ").append(segDays).append("天×").append(rate.stripTrailingZeros().toPlainString());
            segStart = segEnd.plusDays(1);
        }
        // 向下取整到 0.5 天
        BigDecimal raw = sum.divide(BigDecimal.valueOf(365), 4, RoundingMode.DOWN);
        BigDecimal days = raw.multiply(BigDecimal.valueOf(2)).setScale(0, RoundingMode.FLOOR)
                .divide(BigDecimal.valueOf(2));
        r.days = days.max(BigDecimal.ZERO);
        r.detail = truncate(detail.append("；折算").append(raw.setScale(2, RoundingMode.DOWN).toPlainString())
                .append("天，取整").append(r.days.stripTrailingZeros().toPlainString()).append("天").toString());
        return r;
    }

    /**
     * 条例第 4 条排除：当年已批准事假 ≥20 天，或病假达到工龄档月数（2/3/4 个月×21.75 向上取整），返回原因；否则 null。
     */
    private String exclusionReason(Employee emp, int year, LocalDate firstWork, AnnualLeavePolicy policy) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        BigDecimal personal = approvedDays(emp.getId(), LEAVE_TYPE_PERSONAL, yearStart, yearEnd);
        if (personal.compareTo(PERSONAL_EXCLUDE_DAYS) >= 0) {
            return "当年事假已 " + personal.stripTrailingZeros().toPlainString() + " 天（≥20 天），依条例第 4 条不享受当年年假";
        }
        BigDecimal sick = approvedDays(emp.getId(), LEAVE_TYPE_SICK, yearStart, yearEnd);
        if (sick.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        long years = ChronoUnit.YEARS.between(firstWork, yearStart);
        int months = years >= tierYears(policy.getTier3Years(), 20) ? 4
                : years >= tierYears(policy.getTier2Years(), 10) ? 3 : 2;
        BigDecimal threshold = WORKDAYS_PER_MONTH.multiply(BigDecimal.valueOf(months)).setScale(0, RoundingMode.CEILING);
        if (sick.compareTo(threshold) >= 0) {
            return "当年病假已 " + sick.stripTrailingZeros().toPlainString() + " 天（≥" + months + " 个月），依条例第 4 条不享受当年年假";
        }
        return null;
    }

    /** 当年已批准（含已销假）的某类型请假天数合计 */
    private BigDecimal approvedDays(Long employeeId, String leaveType, LocalDate yearStart, LocalDate yearEnd) {
        List<LeaveRequest> list = leaveMapper.selectList(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getEmployeeId, employeeId)
                .eq(LeaveRequest::getLeaveType, leaveType)
                .in(LeaveRequest::getStatus, 1, 4)
                .ge(LeaveRequest::getStartDate, yearStart)
                .le(LeaveRequest::getStartDate, yearEnd));
        return list.stream().map(l -> nvl(l.getDays())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 手动生成全员当年额度（联调用），返回本次新建数量 */
    @Transactional(rollbackFor = Exception.class)
    public int generateAll(int year) {
        int created = 0;
        for (Employee e : employeesInScope(year)) {
            if (findQuota(e.getId(), year) == null) {
                generateYear(e.getId(), year);
                created++;
            }
        }
        log.info("手动生成 {} 年年假额度，新建 {} 条", year, created);
        return created;
    }

    /**
     * 每日 02:30（调度入口见 schedule 包的 AnnualLeaveRefreshJob）：
     * 缺失额度补建；source=AUTO 的额度按新规则重算（年中入职/离职/跨档/条例排除都会反映），
     * 重算值 = 折算天数 + 结转 - 已签放弃，下限为 已用+冻结（提前离职多休部分从工资扣回，不回收为负）。
     */
    public void dailyRefresh() {
        int year = LocalDate.now().getYear();
        int created = recalculateYear(year, false);
        log.info("每日年假刷新完成 year={} 处理={}", year, created);
    }

    /**
     * 重算某年度全员额度（admin 手动触发 / 每日调度）。
     *
     * @param includeResigned 是否包含已离职员工（年度重算历史数据时为 true）
     * @return 新建 + 重算有变化的额度条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int recalculateYear(int year, boolean includeResigned) {
        AnnualLeavePolicy policy = activePolicy();
        int touched = 0;
        for (Employee e : employeesInScope(year)) {
            try {
                LeaveQuota q = findQuota(e.getId(), year);
                if (q == null) {
                    if (includeResigned || isWorking(e)) {
                        generateYear(e.getId(), year);
                        touched++;
                    }
                    continue;
                }
                if (!SOURCE_AUTO.equalsIgnoreCase(q.getSource())) {
                    continue;
                }
                CalcResult calc = calcAnnualDays(e, year, policy);
                BigDecimal waived = waivedDays(q.getId());
                BigDecimal newTotal = calc.days.add(nvl(q.getCarryoverIn())).subtract(waived)
                        .max(nvl(q.getUsedDays()).add(nvl(q.getFrozenDays())));
                if (newTotal.compareTo(nvl(q.getTotalDays())) != 0) {
                    String detail = truncate(calc.detail + "；结转" + nvl(q.getCarryoverIn()).stripTrailingZeros().toPlainString()
                            + "天" + (waived.compareTo(BigDecimal.ZERO) > 0
                            ? "；已签放弃" + waived.stripTrailingZeros().toPlainString() + "天" : ""));
                    int n = quotaMapper.recalcTotal(q.getId(), newTotal, detail);
                    if (n > 0) {
                        touched++;
                    }
                }
            } catch (Exception ex) {
                log.warn("年假额度重算失败 employeeId={} year={}：{}", e.getId(), year, ex.getMessage());
            }
        }
        return touched;
    }

    /** 该额度已签署放弃的结转天数合计 */
    public BigDecimal waivedDays(Long quotaId) {
        return waiverMapper.sumWaiveDays(quotaId);
    }

    // ---------------------------------------------------------------- 年假台账

    /**
     * 年假台账：在职员工按年展示「当年产生 / 上年结转 / 已用 / 剩余」。
     * 当年产生天数用统一折算公式实时计算，不依赖已生成额度（未生成的也会显示应产生天数）。
     */
    public R.Page<AnnualLeaveLedgerVO> pageLedger(String keyword, Integer year, int page, int size) {
        int y = year == null ? LocalDate.now().getYear() : year;
        LambdaQueryWrapper<Employee> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            w.and(q -> q.like(Employee::getName, k).or().like(Employee::getEmpNo, k));
        }
        w.ne(Employee::getStatus, EmployeeStatus.LEFT.getCode());
        w.orderByAsc(Employee::getEmpNo);
        IPage<Employee> ip = employeeMapper.selectPage(new Page<>(page, size), w);
        if (ip.getRecords().isEmpty()) {
            return R.Page.of(List.of(), ip.getTotal());
        }
        Map<Long, String> orgNames = new HashMap<>();
        for (OrgNode n : orgMapper.selectList(new LambdaQueryWrapper<OrgNode>())) {
            orgNames.put(n.getId(), n.getName() == null ? "" : n.getName());
        }
        List<Long> empIds = ip.getRecords().stream().map(Employee::getId).toList();
        Map<Long, LeaveQuota> quotaMap = new HashMap<>();
        for (LeaveQuota q : quotaMapper.selectList(new LambdaQueryWrapper<LeaveQuota>()
                .in(LeaveQuota::getEmployeeId, empIds)
                .eq(LeaveQuota::getLeaveType, LEAVE_TYPE_ANNUAL)
                .eq(LeaveQuota::getPeriodStart, LocalDate.of(y, 1, 1)))) {
            quotaMap.put(q.getEmployeeId(), q);
        }
        AnnualLeavePolicy policy = activePolicy();
        List<AnnualLeaveLedgerVO> rows = new ArrayList<>();
        for (Employee e : ip.getRecords()) {
            AnnualLeaveLedgerVO vo = new AnnualLeaveLedgerVO();
            vo.setEmployeeId(e.getId());
            vo.setEmpNo(e.getEmpNo());
            vo.setName(e.getName());
            vo.setOrgName(orgNames.getOrDefault(e.getOrgId(), ""));
            vo.setHireDate(e.getHireDate() == null ? null : e.getHireDate().toString());
            vo.setResignDate(e.getResignDate() == null ? null : e.getResignDate().toString());
            vo.setStatus(e.getStatus());
            EmployeeStatus st = EmployeeStatus.of(e.getStatus());
            vo.setStatusName(st == null ? "" : st.getLabel());
            vo.setYear(y);
            vo.setTier(tierText(e, y, policy));
            vo.setGeneratedDays(calcAnnualDays(e, y, policy).days);
            LeaveQuota q = quotaMap.get(e.getId());
            if (q == null) {
                vo.setCarryoverIn(BigDecimal.ZERO);
                vo.setCarryoverRemaining(BigDecimal.ZERO);
                vo.setUsedDays(BigDecimal.ZERO);
                vo.setFrozenDays(BigDecimal.ZERO);
                vo.setTotalDays(BigDecimal.ZERO);
                vo.setAvailableDays(BigDecimal.ZERO);
                vo.setMissing(true);
            } else {
                vo.setCarryoverIn(nvl(q.getCarryoverIn()));
                vo.setCarryoverRemaining(nvl(q.getCarryoverIn()).subtract(nvl(q.getCarryoverUsed())).max(BigDecimal.ZERO));
                vo.setUsedDays(nvl(q.getUsedDays()));
                vo.setFrozenDays(nvl(q.getFrozenDays()));
                vo.setTotalDays(nvl(q.getTotalDays()));
                vo.setAvailableDays(nvl(q.getTotalDays()).subtract(nvl(q.getUsedDays())).subtract(nvl(q.getFrozenDays())));
                vo.setCarryoverExpireAt(q.getCarryoverExpireAt() == null ? null : q.getCarryoverExpireAt().toString());
                vo.setCalcDetail(q.getCalcDetail());
                vo.setMissing(false);
            }
            rows.add(vo);
        }
        return R.Page.of(rows, ip.getTotal());
    }

    /**
     * 年假档位文案：按当年在册区间的起止工龄档位展示（5 / 10 / 15；年内跨档显示 5→10）。
     * 当年无在册资格区间（未满 1 年 / 已离职）返回 “—”。
     */
    public String tierText(Employee emp, int year, AnnualLeavePolicy policy) {
        LocalDate firstWork = firstWorkDate(emp);
        if (firstWork == null) {
            return "—";
        }
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        LocalDate from = yearStart;
        if (emp.getHireDate() != null && emp.getHireDate().isAfter(from)) {
            from = emp.getHireDate();
        }
        LocalDate eligibleFrom = firstWork.plusYears(tierYears(policy.getTier1Years(), 1));
        if (eligibleFrom.isAfter(from)) {
            from = eligibleFrom;
        }
        LocalDate to = yearEnd;
        if (emp.getResignDate() != null && emp.getResignDate().isBefore(to)) {
            to = emp.getResignDate();
        }
        if (from.isAfter(to)) {
            return "—";
        }
        String start = tierDaysAt(firstWork, from, policy);
        String end = tierDaysAt(firstWork, to, policy);
        return start.equals(end) ? end : start + "→" + end;
    }

    /** 指定日期的工龄档位天数文案 */
    private String tierDaysAt(LocalDate firstWork, LocalDate date, AnnualLeavePolicy policy) {
        long years = ChronoUnit.YEARS.between(firstWork, date);
        BigDecimal days;
        if (years >= tierYears(policy.getTier3Years(), 20)) {
            days = nvl(policy.getTier3Days());
        } else if (years >= tierYears(policy.getTier2Years(), 10)) {
            days = nvl(policy.getTier2Days());
        } else {
            days = nvl(policy.getTier1Days());
        }
        return days.stripTrailingZeros().toPlainString();
    }

    // ---------------------------------------------------------------- 工具

    private boolean isWorking(Employee e) {
        Integer s = e.getStatus();
        return s != null && (s == EmployeeStatus.PROBATION.getCode() || s == EmployeeStatus.ACTIVE.getCode());
    }

    /** 年度重算范围：在职 + 离职流程中 + 待关闭账号 + 当年及之后才离职的（年中离职按在册折算） */
    private List<Employee> employeesInScope(int year) {
        return employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .in(Employee::getStatus, EmployeeStatus.PROBATION.getCode(), EmployeeStatus.ACTIVE.getCode(),
                        EmployeeStatus.OFFBOARDING.getCode(), EmployeeStatus.PENDING_ACCOUNT_CLOSE.getCode())
                .or(w -> w.eq(Employee::getStatus, EmployeeStatus.LEFT.getCode())
                        .ge(Employee::getResignDate, LocalDate.of(year, 1, 1))));
    }

    private BigDecimal available(LeaveQuota q) {
        return nvl(q.getTotalDays()).subtract(nvl(q.getUsedDays())).subtract(nvl(q.getFrozenDays()));
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

    private int tierYears(Integer v, int def) {
        return v == null ? def : v;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String truncate(String s) {
        return s != null && s.length() > CALC_DETAIL_MAX ? s.substring(0, CALC_DETAIL_MAX) : s;
    }

    /** 折算结果 */
    public static class CalcResult {
        public BigDecimal days = BigDecimal.ZERO;
        public String detail = "";
    }
}
