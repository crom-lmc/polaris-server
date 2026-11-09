package com.laimengchao.sso.ehr.module.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laimengchao.sso.ehr.module.dashboard.vo.DashboardSummaryVO;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveRequest;
import com.laimengchao.sso.ehr.module.leave.mapper.LeaveMapper;
import com.laimengchao.sso.ehr.module.leave.service.LeaveService;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import com.laimengchao.sso.ehr.security.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 人力看板汇总。
 * <p>
 * 口径固定为「先按数据范围取员工集合，再在内存里统计」——员工表规模在万级以内，
 * 比拼多段 JOIN + GROUP BY 更简单，也能保证卡片与列表筛选条件完全同源。
 */
@Service
@RequiredArgsConstructor
public class DashboardSummaryService {

    /** 员工生命周期状态（与 Employee.status 注释一致） */
    private static final int STATUS_ONBOARDING = 10;
    private static final int STATUS_ONBOARD_APPROVING = 20;
    private static final int STATUS_ONBOARD_APPROVED = 25;
    private static final int STATUS_PROBATION = 30;
    private static final int STATUS_ACTIVE = 40;
    private static final int STATUS_TERMINATING = 50;
    private static final int STATUS_RESIGNED = 60;

    private final EmployeeMapper employeeMapper;
    private final OrgNodeMapper orgMapper;
    private final LeaveMapper leaveMapper;
    private final DataScopeService dataScope;
    private final LeaveService leaveService;

    public DashboardSummaryVO summary(Long orgId, LocalDate from, LocalDate to) {
        DashboardSummaryVO vo = new DashboardSummaryVO();
        List<Employee> employees = scopedEmployees(orgId);
        if (employees.isEmpty()) {
            return vo;
        }

        // ---------- 概览：状态人数 + 本月入职/离职/转正 ----------
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        DashboardSummaryVO.Overview ov = vo.getOverview();
        for (Employee e : employees) {
            Integer status = e.getStatus();
            if (status == null) {
                continue;
            }
            switch (status) {
                case STATUS_ACTIVE -> ov.setActive(ov.getActive() + 1);
                case STATUS_PROBATION -> ov.setProbation(ov.getProbation() + 1);
                case STATUS_ONBOARDING, STATUS_ONBOARD_APPROVING, STATUS_ONBOARD_APPROVED ->
                        ov.setOnboarding(ov.getOnboarding() + 1);
                case STATUS_TERMINATING -> ov.setTerminating(ov.getTerminating() + 1);
                default -> {
                }
            }
            if (e.getHireDate() != null && inRange(e.getHireDate(), monthStart, monthEnd)
                    && status != STATUS_ONBOARDING && status != STATUS_ONBOARD_APPROVING
                    && status != STATUS_ONBOARD_APPROVED) {
                ov.setOnboardThisMonth(ov.getOnboardThisMonth() + 1);
            }
            if (status == STATUS_RESIGNED && e.getResignDate() != null
                    && inRange(e.getResignDate(), monthStart, monthEnd)) {
                ov.setResignThisMonth(ov.getResignThisMonth() + 1);
            }
            if (status == STATUS_ACTIVE && e.getExpectedRegularizeDate() != null
                    && inRange(e.getExpectedRegularizeDate(), monthStart, monthEnd)) {
                ov.setRegularizedThisMonth(ov.getRegularizedThisMonth() + 1);
            }
        }

        // ---------- 组织名称 / 编制 ----------
        List<OrgNode> orgs = orgMapper.selectList(Wrappers.<OrgNode>lambdaQuery()
                .eq(OrgNode::getStatus, 1));
        Map<Long, OrgNode> orgById = orgs.stream()
                .collect(Collectors.toMap(OrgNode::getId, x -> x, (a, b) -> a));

        // ---------- 部门人力分布 ----------
        Map<Long, Long> actualByOrg = employees.stream()
                .filter(e -> e.getOrgId() != null && isOnDuty(e.getStatus()))
                .collect(Collectors.groupingBy(Employee::getOrgId, Collectors.counting()));
        for (Map.Entry<Long, Long> entry : actualByOrg.entrySet()) {
            OrgNode org = orgById.get(entry.getKey());
            DashboardSummaryVO.OrgDistribution d = new DashboardSummaryVO.OrgDistribution();
            d.setOrgId(entry.getKey());
            d.setOrgName(org == null ? "未知组织" : org.getName());
            d.setType(org == null ? "" : org.getType());
            d.setEmployees(entry.getValue());
            vo.getOrgDistribution().add(d);
        }
        vo.getOrgDistribution().sort(Comparator.comparing(DashboardSummaryVO.OrgDistribution::getEmployees)
                .reversed());

        // ---------- 编制 vs 实有 ----------
        long totalHeadcount = 0;
        long totalActual = 0;
        for (OrgNode org : orgs) {
            int headcount = org.getHeadcount() == null ? 0 : Math.max(org.getHeadcount(), 0);
            long actual = actualByOrg.getOrDefault(org.getId(), 0L);
            if (headcount == 0 && actual == 0) {
                continue;
            }
            DashboardSummaryVO.HeadcountItem item = new DashboardSummaryVO.HeadcountItem();
            item.setOrgId(org.getId());
            item.setOrgName(org.getName());
            item.setType(org.getType());
            item.setHeadcount(headcount);
            item.setActual(actual);
            item.setUtilization(headcount > 0
                    ? BigDecimal.valueOf(actual * 100.0 / headcount)
                    .setScale(1, RoundingMode.HALF_UP).doubleValue()
                    : null);
            vo.getHeadcount().add(item);
            totalHeadcount += headcount;
            totalActual += actual;
        }
        vo.getHeadcount().sort(Comparator
                .comparing((DashboardSummaryVO.HeadcountItem x) -> x.getUtilization() == null ? -1 : x.getUtilization())
                .reversed());
        DashboardSummaryVO.HeadcountTotal total = vo.getHeadcountTotal();
        total.setHeadcount(totalHeadcount);
        total.setActual(totalActual);
        total.setUtilization(totalHeadcount > 0
                ? BigDecimal.valueOf(totalActual * 100.0 / totalHeadcount)
                .setScale(1, RoundingMode.HALF_UP).doubleValue()
                : null);

        // ---------- 请假趋势：默认近 6 个月，只统计已通过 ----------
        LocalDate trendFrom = from != null ? from
                : YearMonth.now().minusMonths(5).atDay(1);
        LocalDate trendTo = to != null ? to : today;
        if (trendTo.isBefore(trendFrom)) {
            LocalDate tmp = trendFrom;
            trendFrom = trendTo;
            trendTo = tmp;
        }
        List<Long> employeeIds = employees.stream().map(Employee::getId)
                .filter(x -> x != null).distinct().collect(Collectors.toList());
        Map<String, String> typeNames = leaveService.typeNames();
        LambdaQueryWrapper<LeaveRequest> lw = Wrappers.<LeaveRequest>lambdaQuery()
                .in(LeaveRequest::getEmployeeId, employeeIds)
                .eq(LeaveRequest::getStatus, 1)
                .ge(LeaveRequest::getStartDate, trendFrom)
                .le(LeaveRequest::getStartDate, trendTo)
                .orderByAsc(LeaveRequest::getStartDate);
        List<LeaveRequest> leaves = leaveMapper.selectList(lw);
        // month + type -> days
        Map<String, Double> trend = new LinkedHashMap<>();
        for (LeaveRequest l : leaves) {
            if (l.getStartDate() == null) {
                continue;
            }
            String month = YearMonth.from(l.getStartDate()).toString();
            String type = l.getLeaveType() == null ? "OTHER" : l.getLeaveType();
            double days = l.getDays() == null ? 0d : l.getDays().doubleValue();
            trend.merge(month + "|" + type, days, Double::sum);
        }
        for (Map.Entry<String, Double> entry : trend.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            DashboardSummaryVO.LeaveTrend t = new DashboardSummaryVO.LeaveTrend();
            t.setMonth(parts[0]);
            t.setLeaveType(parts[1]);
            t.setLeaveTypeName(typeNames.getOrDefault(parts[1], parts[1]));
            t.setDays(BigDecimal.valueOf(entry.getValue()).setScale(2, RoundingMode.HALF_UP).doubleValue());
            vo.getLeaveTrend().add(t);
        }
        return vo;
    }

    /** 数据范围 + 可选组织（含下级）过滤 */
    private List<Employee> scopedEmployees(Long orgId) {
        LambdaQueryWrapper<Employee> w = Wrappers.lambdaQuery();
        dataScope.applyTo(w);
        if (orgId != null) {
            List<Long> ids = subtreeIds(orgId);
            if (ids.isEmpty()) {
                return List.of();
            }
            w.in(Employee::getOrgId, ids);
        }
        return employeeMapper.selectList(w);
    }

    private List<Long> subtreeIds(Long rootOrgId) {
        OrgNode root = orgMapper.selectById(rootOrgId);
        if (root == null) {
            return List.of();
        }
        if (root.getPath() == null || root.getPath().isBlank()) {
            return List.of(rootOrgId);
        }
        List<OrgNode> all = orgMapper.selectList(Wrappers.<OrgNode>lambdaQuery()
                .likeRight(OrgNode::getPath, root.getPath()));
        List<Long> ids = all.stream().map(OrgNode::getId).collect(Collectors.toList());
        return ids.isEmpty() ? List.of(rootOrgId) : ids;
    }

    private static boolean isOnDuty(Integer status) {
        return status != null && (status == STATUS_PROBATION || status == STATUS_ACTIVE);
    }

    private static boolean inRange(LocalDate date, LocalDate start, LocalDate end) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
