package com.laimengchao.sso.ehr.module.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.approval.entity.RegularizeApply;
import com.laimengchao.sso.ehr.module.approval.mapper.RegularizeApplyMapper;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceDaily;
import com.laimengchao.sso.ehr.module.attendance.entity.Overtime;
import com.laimengchao.sso.ehr.module.attendance.mapper.AttendanceDailyMapper;
import com.laimengchao.sso.ehr.module.attendance.mapper.OvertimeMapper;
import com.laimengchao.sso.ehr.module.contract.entity.Contract;
import com.laimengchao.sso.ehr.module.contract.mapper.ContractMapper;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.employee.service.SeniorityService;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveRequest;
import com.laimengchao.sso.ehr.module.leave.mapper.LeaveMapper;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.AmountMonth;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.AttendanceLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.AttendanceRow;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.ContractLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.ContractRow;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.CountItem;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.DailyCount;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.LeaveLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.LeaveRow;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.LeaveTypeStat;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.MonthCount;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.MovementLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.MovementMonth;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.MovementRow;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.OrgAmount;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.OrgNet;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.OrgOption;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.OvertimeLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.OvertimeOrgStat;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.OvertimeRow;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.RecruitmentLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.RecruitmentPositionStat;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.RecruitmentRow;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.RegularizationLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.RegularizationRow;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.RegularizeOrgStat;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.TypeAmount;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.WorkforceLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.WorkforceRow;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import com.laimengchao.sso.ehr.module.recruit.entity.Candidate;
import com.laimengchao.sso.ehr.module.recruit.entity.Interview;
import com.laimengchao.sso.ehr.module.recruit.entity.Offer;
import com.laimengchao.sso.ehr.module.recruit.mapper.CandidateMapper;
import com.laimengchao.sso.ehr.module.recruit.mapper.InterviewMapper;
import com.laimengchao.sso.ehr.module.recruit.mapper.OfferMapper;
import com.laimengchao.sso.ehr.security.DataScopeService;
import com.laimengchao.sso.settings.client.support.SettingsClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** 人力台账查询。所有业务查询均先叠加组织数据权限，再做用户筛选。 */
@Service
@RequiredArgsConstructor
public class HrLedgerService {

    private static final List<String> CONTRACT_TYPES = List.of("FIXED", "UNFIXED", "INTERN", "LABOR");
    private static final List<String> ATTENDANCE_TYPES = List.of("LATE", "EARLY", "MISSING", "ABSENT");
    private static final List<String> AGE_GROUPS = List.of("<25", "25-29", "30-34", "35-39", "40-44", "45-49", "50+", "UNKNOWN");
    private static final List<String> SENIORITY_GROUPS = List.of("<1", "1-2", "3-4", "5-9", "10+", "UNKNOWN");
    private static final List<String> OVERTIME_TYPES = List.of("NORMAL", "RESTDAY", "HOLIDAY");
    private static final Map<String, String> DEFAULT_LEAVE_TYPES = defaultLeaveTypeNames();

    private final ContractMapper contractMapper;
    private final EmployeeMapper employeeMapper;
    private final AttendanceDailyMapper attendanceMapper;
    private final OrgNodeMapper orgMapper;
    private final DataScopeService dataScope;
    private final SettingsClient settingsClient;
    private final RegularizeApplyMapper regularizeApplyMapper;
    private final LeaveMapper leaveMapper;
    private final OvertimeMapper overtimeMapper;
    private final CandidateMapper candidateMapper;
    private final InterviewMapper interviewMapper;
    private final OfferMapper offerMapper;

    public ContractLedger contracts(Long orgId, String expiry, String type, String keyword,
                                    int page, int size) {
        List<OrgOption> orgs = accessibleOrgs();
        Map<Long, String> orgNames = orgNames(orgs);
        List<Long> scopedEmployeeIds = scopedEmployeeIds(orgId);
        if (scopedEmployeeIds.isEmpty()) {
            return emptyContract(orgs, page, size);
        }
        LambdaQueryWrapper<Contract> w = Wrappers.lambdaQuery();
        dataScope.applyOrgScope(w, Contract::getOrgCode);
        w.in(Contract::getEmployeeId, scopedEmployeeIds)
                .in(Contract::getStatus, "ACTIVE", "EXPIRED").isNotNull(Contract::getEndDate);
        List<Contract> contracts = contractMapper.selectList(w);
        Map<Long, Employee> employees = employeesOf(contracts.stream()
                .map(Contract::getEmployeeId).filter(Objects::nonNull).distinct().toList());
        String normalizedType = normalize(type);
        String normalizedExpiry = normalize(expiry);
        String normalizedKeyword = keyword(keyword);
        LocalDate today = LocalDate.now();

        List<ContractRow> rows = new ArrayList<>();
        for (Contract c : contracts) {
            Employee emp = employees.get(c.getEmployeeId());
            long remaining = ChronoUnit.DAYS.between(today, c.getEndDate());
            if (!matchesExpiry(remaining, normalizedExpiry)
                    || !"ALL".equals(normalizedType) && !normalizedType.equals(c.getContractType())
                    || !matchesContractKeyword(c, emp, normalizedKeyword)) {
                continue;
            }
            ContractRow row = new ContractRow();
            row.setId(String.valueOf(c.getId()));
            row.setEmployeeId(c.getEmployeeId());
            row.setName(emp == null ? "" : nvl(emp.getName()));
            row.setEmpNo(emp == null ? "" : nvl(emp.getEmpNo()));
            row.setOrg(orgNames.getOrDefault(emp == null ? null : emp.getOrgId(), "未知组织"));
            row.setContractNo(nvl(c.getContractNo()));
            row.setType(c.getContractType());
            row.setStartDate(c.getStartDate());
            row.setEndDate(c.getEndDate());
            row.setRemainingDays(remaining);
            row.setStatus(c.getStatus());
            rows.add(row);
        }
        rows.sort(Comparator.comparing(ContractRow::getEndDate)
                .thenComparing(ContractRow::getEmpNo).thenComparing(ContractRow::getId));

        ContractLedger result = new ContractLedger();
        result.setOrgs(orgs);
        result.setWithin30(rows.stream().filter(x -> x.getRemainingDays() >= 0 && x.getRemainingDays() <= 30).count());
        result.setWithin60(rows.stream().filter(x -> x.getRemainingDays() > 30 && x.getRemainingDays() <= 60).count());
        result.setWithin90(rows.stream().filter(x -> x.getRemainingDays() > 60 && x.getRemainingDays() <= 90).count());
        result.setExpired(rows.stream().filter(x -> x.getRemainingDays() < 0).count());
        YearMonth month = YearMonth.now();
        for (int i = 0; i < 6; i++, month = month.plusMonths(1)) {
            YearMonth current = month;
            MonthCount item = new MonthCount();
            item.setMonth(month.toString());
            item.setValue(rows.stream().filter(x -> YearMonth.from(x.getEndDate()).equals(current)).count());
            result.getMonthly().add(item);
        }
        result.setTypes(countItems(rows, CONTRACT_TYPES, ContractRow::getType, HrLedgerService::contractTypeName));
        result.setPage(page(rows, page, size));
        return result;
    }

    public MovementLedger movement(String fromMonth, String toMonth, Long orgId, String event,
                                   int page, int size) {
        YearMonth from = parseMonth(fromMonth, YearMonth.now().minusMonths(11));
        YearMonth to = parseMonth(toMonth, YearMonth.now());
        if (to.isBefore(from)) {
            YearMonth tmp = from;
            from = to;
            to = tmp;
        }
        if (ChronoUnit.MONTHS.between(from, to) > 119) {
            throw new BizException(ErrorCode.PARAM_ERROR, "月份跨度不能超过 120 个月");
        }
        List<OrgOption> orgs = accessibleOrgs();
        Map<Long, String> orgNames = orgNames(orgs);
        List<Employee> employees = scopedEmployees(orgId);
        LocalDate start = from.atDay(1);
        LocalDate end = to.atEndOfMonth();
        List<MovementRow> allEvents = new ArrayList<>();
        for (Employee emp : employees) {
            if (hasEntered(emp) && inRange(emp.getHireDate(), start, end)) {
                allEvents.add(movementRow(emp, emp.getHireDate(), "ONBOARD",
                        nvl(emp.getPosition()).isBlank() ? "员工入职" : emp.getPosition() + "入职", orgNames));
            }
            if (Integer.valueOf(40).equals(emp.getStatus()) && Integer.valueOf(3).equals(emp.getRegularizeStatus())
                    && inRange(emp.getExpectedRegularizeDate(), start, end)) {
                allEvents.add(movementRow(emp, emp.getExpectedRegularizeDate(), "REGULAR", "试用期转正", orgNames));
            }
            if (Integer.valueOf(60).equals(emp.getStatus()) && inRange(emp.getResignDate(), start, end)) {
                String reason = nvl(emp.getResignReason());
                allEvents.add(movementRow(emp, emp.getResignDate(), "RESIGN",
                        reason.isBlank() ? "员工离职" : reason, orgNames));
            }
        }
        String normalizedEvent = normalize(event);
        List<MovementRow> rows = allEvents.stream()
                .filter(x -> "ALL".equals(normalizedEvent) || normalizedEvent.equals(x.getEvent()))
                .sorted(Comparator.comparing(MovementRow::getDate).reversed()
                        .thenComparing(MovementRow::getEmpNo).thenComparing(MovementRow::getEvent))
                .toList();
        long hires = rows.stream().filter(x -> "ONBOARD".equals(x.getEvent())).count();
        long resigns = rows.stream().filter(x -> "RESIGN".equals(x.getEvent())).count();

        MovementLedger result = new MovementLedger();
        result.setOrgs(orgs);
        result.setHires(hires);
        result.setResigns(resigns);
        result.setNet(hires - resigns);
        result.setTurnoverRate(turnoverRate(employees, start, end, resigns));
        YearMonth month = from;
        while (!month.isAfter(to)) {
            YearMonth current = month;
            MovementMonth item = new MovementMonth();
            item.setMonth(month.toString());
            item.setHires(rows.stream().filter(x -> "ONBOARD".equals(x.getEvent())
                    && YearMonth.from(x.getDate()).equals(current)).count());
            item.setResigns(rows.stream().filter(x -> "RESIGN".equals(x.getEvent())
                    && YearMonth.from(x.getDate()).equals(current)).count());
            result.getMonthly().add(item);
            month = month.plusMonths(1);
        }
        Map<String, List<MovementRow>> byOrg = rows.stream()
                .collect(Collectors.groupingBy(MovementRow::getOrg, LinkedHashMap::new, Collectors.toList()));
        byOrg.forEach((org, items) -> {
            long orgHires = items.stream().filter(x -> "ONBOARD".equals(x.getEvent())).count();
            long orgResigns = items.stream().filter(x -> "RESIGN".equals(x.getEvent())).count();
            OrgNet net = new OrgNet();
            net.setOrg(org);
            net.setValue(orgHires - orgResigns);
            result.getOrgNet().add(net);
        });
        result.getOrgNet().sort(Comparator.comparing(OrgNet::getValue).reversed());
        result.setPage(page(rows, page, size));
        return result;
    }

    public AttendanceLedger attendance(String month, Long orgId, String type, String status,
                                       String keyword, int page, int size) {
        YearMonth ym = parseMonth(month, YearMonth.now());
        List<OrgOption> orgs = accessibleOrgs();
        Map<Long, String> orgNames = orgNames(orgs);
        List<Long> scopedEmployeeIds = scopedEmployeeIds(orgId);
        if (scopedEmployeeIds.isEmpty()) {
            return emptyAttendance(orgs, page, size);
        }
        LambdaQueryWrapper<AttendanceDaily> w = Wrappers.lambdaQuery();
        dataScope.applyOrgScope(w, AttendanceDaily::getOrgCode);
        w.in(AttendanceDaily::getEmployeeId, scopedEmployeeIds)
                .in(AttendanceDaily::getStatus, "LATE", "EARLY", "LATE_EARLY", "MISSING_PUNCH", "ABSENT")
                .ge(AttendanceDaily::getWorkDate, ym.atDay(1))
                .le(AttendanceDaily::getWorkDate, ym.atEndOfMonth());
        List<AttendanceDaily> dailyRows = attendanceMapper.selectList(w);
        Map<Long, Employee> employees = employeesOf(dailyRows.stream()
                .map(AttendanceDaily::getEmployeeId).filter(Objects::nonNull).distinct().toList());
        List<AttendanceRow> expanded = new ArrayList<>();
        for (AttendanceDaily daily : dailyRows) {
            Employee emp = employees.get(daily.getEmployeeId());
            if ("LATE".equals(daily.getStatus()) || "LATE_EARLY".equals(daily.getStatus())) {
                expanded.add(attendanceRow(daily, emp, "LATE", number(daily.getLateMinutes()), orgNames));
            }
            if ("EARLY".equals(daily.getStatus()) || "LATE_EARLY".equals(daily.getStatus())) {
                expanded.add(attendanceRow(daily, emp, "EARLY", number(daily.getEarlyMinutes()), orgNames));
            }
            if ("MISSING_PUNCH".equals(daily.getStatus())) {
                expanded.add(attendanceRow(daily, emp, "MISSING", 0, orgNames));
            }
            if ("ABSENT".equals(daily.getStatus())) {
                double hours = daily.getAbsentHours() == null ? number(daily.getAbsentMinutes()) / 60.0
                        : daily.getAbsentHours().doubleValue();
                expanded.add(attendanceRow(daily, emp, "ABSENT", hours, orgNames));
            }
        }
        String normalizedType = normalize(type);
        String normalizedStatus = normalize(status);
        String normalizedKeyword = keyword(keyword);
        List<AttendanceRow> rows = expanded.stream()
                .filter(x -> "ALL".equals(normalizedType) || normalizedType.equals(x.getType()))
                .filter(x -> "ALL".equals(normalizedStatus) || normalizedStatus.equals(x.getStatus()))
                .filter(x -> normalizedKeyword.isEmpty()
                        || (nvl(x.getName()) + nvl(x.getEmpNo())).toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .sorted(Comparator.comparing(AttendanceRow::getDate).reversed()
                        .thenComparing(AttendanceRow::getEmpNo).thenComparing(AttendanceRow::getType))
                .toList();

        AttendanceLedger result = new AttendanceLedger();
        result.setOrgs(orgs);
        result.setTotal(rows.size());
        result.setLate(countAttendance(rows, "LATE"));
        result.setEarly(countAttendance(rows, "EARLY"));
        result.setMissing(countAttendance(rows, "MISSING"));
        result.setAbsent(countAttendance(rows, "ABSENT"));
        result.setLateMinutes(attendanceValue(rows, "LATE"));
        result.setEarlyMinutes(attendanceValue(rows, "EARLY"));
        result.setTypes(countItems(rows, ATTENDANCE_TYPES, AttendanceRow::getType,
                HrLedgerService::attendanceTypeName));
        Map<LocalDate, Long> byDate = rows.stream().collect(Collectors.groupingBy(
                AttendanceRow::getDate, LinkedHashMap::new, Collectors.counting()));
        byDate.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            DailyCount item = new DailyCount();
            item.setDate(entry.getKey().toString());
            item.setValue(entry.getValue());
            result.getDaily().add(item);
        });
        result.setPage(page(rows, page, size));
        return result;
    }

    public WorkforceLedger workforce(Long orgId, String status, String position, String keyword,
                                     int page, int size) {
        validatePage(page, size);
        Integer statusFilter = parseIntFilter(status, Set.of(10, 20, 25, 30, 40, 50, 55, 60), "员工状态");
        String normalizedPosition = position == null ? "" : position.trim();
        String normalizedKeyword = keyword(keyword);
        List<OrgOption> orgs = accessibleOrgs();
        Map<Long, String> orgNames = orgNames(orgs);
        Map<String, String> educationNames = settingsClient.bizMap("education");
        LocalDate today = LocalDate.now();
        List<WorkforceRow> rows = new ArrayList<>();
        for (Employee emp : scopedEmployees(orgId)) {
            if (statusFilter != null && !statusFilter.equals(emp.getStatus())
                    || !normalizedPosition.isEmpty() && !normalizedPosition.equalsIgnoreCase(nvl(emp.getPosition()).trim())
                    || !matchesKeyword(emp.getName(), emp.getEmpNo(), "", normalizedKeyword)) {
                continue;
            }
            WorkforceRow row = new WorkforceRow();
            row.setId(idString(emp.getId()));
            row.setEmployeeId(idString(emp.getId()));
            row.setName(nvl(emp.getName()));
            row.setEmpNo(nvl(emp.getEmpNo()));
            row.setOrg(orgNames.getOrDefault(emp.getOrgId(), "未知组织"));
            row.setPosition(nvl(emp.getPosition()));
            row.setStatus(emp.getStatus());
            row.setHireDate(emp.getHireDate());
            row.setAge(age(emp.getBirthday(), today));
            row.setEducation(nvl(emp.getEducation()).trim());
            row.setEducationName(educationName(row.getEducation(), educationNames));
            row.setSeniorityYears(seniorityYears(emp, today));
            rows.add(row);
        }
        rows.sort(Comparator.comparing(WorkforceRow::getOrg)
                .thenComparing(WorkforceRow::getEmpNo).thenComparing(WorkforceRow::getId));
        List<WorkforceRow> activeRows = rows.stream().filter(HrLedgerService::activeEmployee).toList();

        WorkforceLedger result = new WorkforceLedger();
        result.setOrgs(orgs);
        result.setActive(activeRows.size());
        result.setProbation(rows.stream().filter(x -> Integer.valueOf(30).equals(x.getStatus())).count());
        result.setPending(rows.stream().filter(x -> x.getStatus() != null
                && (x.getStatus() == 10 || x.getStatus() == 20 || x.getStatus() == 25)).count());
        result.setOffboarding(rows.stream().filter(x -> Integer.valueOf(50).equals(x.getStatus())).count());
        activeRows.stream().collect(Collectors.groupingBy(WorkforceRow::getOrg, LinkedHashMap::new, Collectors.counting()))
                .forEach((org, count) -> result.getByOrg().add(countItem(org, org, count)));
        result.setEducations(educationCountItems(activeRows, educationNames));
        result.setAgeGroups(countItems(activeRows, AGE_GROUPS, x -> ageGroup(x.getAge()), HrLedgerService::groupName));
        result.setSeniorityGroups(countItems(activeRows, SENIORITY_GROUPS,
                x -> seniorityGroup(x.getSeniorityYears()), HrLedgerService::groupName));
        result.setPage(page(rows, page, size));
        return result;
    }

    public RegularizationLedger regularization(Long orgId, String expiry, String approvalStatus,
                                               String keyword, int page, int size) {
        validatePage(page, size);
        String normalizedExpiry = requireChoice(expiry, Set.of("ALL", "OVERDUE", "30", "60", "LATER"), "到期筛选");
        Integer approvalFilter = parseIntFilter(approvalStatus, Set.of(-1, 0, 1, 2), "转正审批状态");
        String normalizedKeyword = keyword(keyword);
        List<OrgOption> orgs = accessibleOrgs();
        Map<Long, String> orgNames = orgNames(orgs);
        List<Employee> employees = scopedEmployees(orgId).stream()
                .filter(x -> Integer.valueOf(30).equals(x.getStatus())).toList();
        List<Long> employeeIds = employees.stream().map(Employee::getId).filter(Objects::nonNull).toList();
        Map<Long, LocalDate> latestApplications = new HashMap<>();
        if (!employeeIds.isEmpty()) {
            LambdaQueryWrapper<RegularizeApply> w = Wrappers.lambdaQuery();
            dataScope.applyOrgScope(w, RegularizeApply::getOrgCode);
            w.in(RegularizeApply::getEmployeeId, employeeIds)
                    .orderByAsc(RegularizeApply::getCreatedAt).orderByAsc(RegularizeApply::getId);
            for (RegularizeApply apply : regularizeApplyMapper.selectList(w)) {
                if (apply.getEmployeeId() != null && apply.getCreatedAt() != null) {
                    latestApplications.put(apply.getEmployeeId(), apply.getCreatedAt().toLocalDate());
                }
            }
        }

        LocalDate today = LocalDate.now();
        List<RegularizationRow> rows = new ArrayList<>();
        for (Employee emp : employees) {
            LocalDate expected = emp.getExpectedRegularizeDate();
            Long remaining = expected == null ? null : ChronoUnit.DAYS.between(today, expected);
            int approval = emp.getRegularizeStatus() == null ? 0 : emp.getRegularizeStatus();
            if (!matchesRegularizationExpiry(remaining, normalizedExpiry)
                    || approvalFilter != null && approvalFilter != approval
                    || !matchesKeyword(emp.getName(), emp.getEmpNo(), "", normalizedKeyword)) {
                continue;
            }
            RegularizationRow row = new RegularizationRow();
            row.setId(idString(emp.getId()));
            row.setEmployeeId(idString(emp.getId()));
            row.setName(nvl(emp.getName()));
            row.setEmpNo(nvl(emp.getEmpNo()));
            row.setOrg(orgNames.getOrDefault(emp.getOrgId(), "未知组织"));
            row.setPosition(nvl(emp.getPosition()));
            row.setHireDate(emp.getHireDate());
            row.setProbationMonths(emp.getProbationMonths());
            row.setExpectedRegularizeDate(expected);
            row.setRemainingDays(remaining);
            row.setLastApplyDate(latestApplications.get(emp.getId()));
            row.setApprovalStatus(approval);
            rows.add(row);
        }
        rows.sort(Comparator.comparing(RegularizationRow::getExpectedRegularizeDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(RegularizationRow::getEmpNo).thenComparing(RegularizationRow::getId));

        RegularizationLedger result = new RegularizationLedger();
        result.setOrgs(orgs);
        result.setCurrent(rows.size());
        result.setOverdue(rows.stream().filter(x -> x.getRemainingDays() != null && x.getRemainingDays() < 0).count());
        result.setDue30(rows.stream().filter(HrLedgerService::dueWithin30).count());
        result.setApproving(rows.stream().filter(x -> x.getApprovalStatus() == 1 || x.getApprovalStatus() == 2).count());
        YearMonth firstMonth = YearMonth.now();
        LocalDate monthlyEnd = firstMonth.plusMonths(5).atEndOfMonth();
        for (int i = 0; i < 6; i++) {
            YearMonth month = firstMonth.plusMonths(i);
            MonthCount item = new MonthCount();
            item.setMonth(month.toString());
            item.setValue(rows.stream().filter(x -> x.getExpectedRegularizeDate() != null
                    && !x.getExpectedRegularizeDate().isBefore(today)
                    && !x.getExpectedRegularizeDate().isAfter(monthlyEnd)
                    && YearMonth.from(x.getExpectedRegularizeDate()).equals(month)).count());
            result.getMonthly().add(item);
        }
        rows.stream().collect(Collectors.groupingBy(RegularizationRow::getOrg, LinkedHashMap::new, Collectors.toList()))
                .forEach((org, items) -> {
                    RegularizeOrgStat stat = new RegularizeOrgStat();
                    stat.setOrg(org);
                    stat.setOverdue(items.stream().filter(x -> x.getRemainingDays() != null && x.getRemainingDays() < 0).count());
                    stat.setDue30(items.stream().filter(HrLedgerService::dueWithin30).count());
                    result.getByOrg().add(stat);
                });
        result.setApprovalStatuses(countItems(rows, List.of("-1", "0", "1", "2"),
                x -> String.valueOf(x.getApprovalStatus()), HrLedgerService::regularizeStatusName));
        result.setPage(page(rows, page, size));
        return result;
    }

    public LeaveLedger leaves(String from, String to, Long orgId, String type, String status,
                              String keyword, int page, int size) {
        validatePage(page, size);
        DateRange range = dateRange(from, to);
        Integer statusFilter = parseIntFilter(status, Set.of(0, 1, 2, 3, 4), "请假状态");
        String normalizedType = normalize(type);
        String normalizedKeyword = keyword(keyword);
        List<OrgOption> orgs = accessibleOrgs();
        Map<Long, String> orgNames = orgNames(orgs);
        List<Employee> scopedEmployees = scopedEmployees(orgId);
        Map<Long, Employee> employees = employeeMap(scopedEmployees);
        List<Long> employeeIds = scopedEmployees.stream().map(Employee::getId).filter(Objects::nonNull).toList();
        List<LeaveRequest> requests = new ArrayList<>();
        if (!employeeIds.isEmpty()) {
            LambdaQueryWrapper<LeaveRequest> w = Wrappers.lambdaQuery();
            dataScope.applyOrgScope(w, LeaveRequest::getOrgCode);
            w.in(LeaveRequest::getEmployeeId, employeeIds)
                    .ge(LeaveRequest::getStartDate, range.start())
                    .le(LeaveRequest::getStartDate, range.end());
            requests = leaveMapper.selectList(w);
        }
        Map<String, String> configuredTypes = configuredLeaveTypeNames();
        Map<String, String> rowTypeNames = new LinkedHashMap<>(configuredTypes);
        requests.forEach(x -> rowTypeNames.putIfAbsent(canonicalType(x.getLeaveType()),
                fallbackLeaveTypeName(x.getLeaveType())));

        List<LeaveRow> rows = new ArrayList<>();
        for (LeaveRequest request : requests) {
            Employee emp = employees.get(request.getEmployeeId());
            String rowType = canonicalType(request.getLeaveType());
            if (!"ALL".equals(normalizedType) && !normalizedType.equals(rowType)
                    || statusFilter != null && !statusFilter.equals(request.getStatus())
                    || !matchesKeyword(emp == null ? "" : emp.getName(), emp == null ? "" : emp.getEmpNo(),
                    request.getLeaveNo(), normalizedKeyword)) {
                continue;
            }
            LeaveRow row = new LeaveRow();
            row.setId(idString(request.getId()));
            row.setEmployeeId(idString(request.getEmployeeId()));
            row.setLeaveNo(nvl(request.getLeaveNo()));
            row.setName(emp == null ? "" : nvl(emp.getName()));
            row.setEmpNo(emp == null ? "" : nvl(emp.getEmpNo()));
            row.setOrg(orgNames.getOrDefault(emp == null ? null : emp.getOrgId(), "未知组织"));
            row.setType(rowType);
            row.setTypeName(rowTypeNames.getOrDefault(rowType, rowType));
            row.setStartDate(request.getStartDate());
            row.setEndDate(request.getEndDate());
            row.setDays(amount(request.getDays()));
            row.setDayMode(request.getDayMode());
            row.setStatus(request.getStatus());
            row.setReturnDate(request.getReturnDate());
            rows.add(row);
        }
        rows.sort(Comparator.comparing(LeaveRow::getStartDate).reversed()
                .thenComparing(LeaveRow::getEmpNo).thenComparing(LeaveRow::getId));
        Predicate<LeaveRow> effective = HrLedgerService::effectiveLeave;

        LeaveLedger result = new LeaveLedger();
        result.setOrgs(orgs);
        result.setEffectivePeople(rows.stream().filter(effective).map(LeaveRow::getEmployeeId)
                .filter(Objects::nonNull).distinct().count());
        result.setEffectiveDays(sumAmount(rows, effective, LeaveRow::getDays));
        result.setPendingDays(sumAmount(rows, x -> Integer.valueOf(0).equals(x.getStatus()), LeaveRow::getDays));
        result.setPendingCount(rows.stream().filter(x -> Integer.valueOf(0).equals(x.getStatus())).count());
        result.setMonthly(monthlyAmounts(rows, range, LeaveRow::getStartDate, effective, LeaveRow::getDays));
        Map<String, String> chartTypeNames = new LinkedHashMap<>(configuredTypes);
        rows.forEach(x -> chartTypeNames.putIfAbsent(x.getType(), x.getTypeName()));
        chartTypeNames.forEach((key, name) -> {
            LeaveTypeStat stat = new LeaveTypeStat();
            stat.setKey(key);
            stat.setName(name);
            stat.setCount(rows.stream().filter(effective).filter(x -> key.equals(x.getType())).count());
            stat.setDays(sumAmount(rows, effective.and(x -> key.equals(x.getType())), LeaveRow::getDays));
            result.getTypes().add(stat);
        });
        rows.stream().filter(effective)
                .collect(Collectors.groupingBy(LeaveRow::getOrg, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    OrgAmount stat = new OrgAmount();
                    stat.setOrg(entry.getKey());
                    stat.setValue(sumAmount(entry.getValue(), x -> true, LeaveRow::getDays));
                    result.getByOrg().add(stat);
                });
        result.setPage(page(rows, page, size));
        return result;
    }

    public OvertimeLedger overtimes(String from, String to, Long orgId, String type, String status,
                                    String settleStatus, String keyword, int page, int size) {
        validatePage(page, size);
        DateRange range = dateRange(from, to);
        String normalizedType = requireChoice(type, Set.of("ALL", "NORMAL", "RESTDAY", "HOLIDAY"), "加班类型");
        Integer statusFilter = parseIntFilter(status, Set.of(0, 1, 2, 3), "加班状态");
        Integer settleFilter = parseIntFilter(settleStatus, Set.of(0, 1), "结算状态");
        String normalizedKeyword = keyword(keyword);
        List<OrgOption> orgs = accessibleOrgs();
        Map<Long, String> orgNames = orgNames(orgs);
        List<Employee> scopedEmployees = scopedEmployees(orgId);
        Map<Long, Employee> employees = employeeMap(scopedEmployees);
        List<Long> employeeIds = scopedEmployees.stream().map(Employee::getId).filter(Objects::nonNull).toList();
        List<Overtime> overtimes = new ArrayList<>();
        if (!employeeIds.isEmpty()) {
            LambdaQueryWrapper<Overtime> w = Wrappers.lambdaQuery();
            dataScope.applyOrgScope(w, Overtime::getOrgCode);
            w.in(Overtime::getEmployeeId, employeeIds)
                    .ge(Overtime::getWorkDate, range.start())
                    .le(Overtime::getWorkDate, range.end());
            overtimes = overtimeMapper.selectList(w);
        }

        List<OvertimeRow> rows = new ArrayList<>();
        for (Overtime overtime : overtimes) {
            Employee emp = employees.get(overtime.getEmployeeId());
            String rowType = canonicalType(overtime.getType());
            if (!"ALL".equals(normalizedType) && !normalizedType.equals(rowType)
                    || statusFilter != null && !statusFilter.equals(overtime.getStatus())
                    || settleFilter != null && !settleFilter.equals(overtime.getSettleStatus())
                    || !matchesKeyword(emp == null ? "" : emp.getName(), emp == null ? "" : emp.getEmpNo(),
                    overtime.getOvertimeNo(), normalizedKeyword)) {
                continue;
            }
            OvertimeRow row = new OvertimeRow();
            row.setId(idString(overtime.getId()));
            row.setEmployeeId(idString(overtime.getEmployeeId()));
            row.setOvertimeNo(nvl(overtime.getOvertimeNo()));
            row.setName(emp == null ? "" : nvl(emp.getName()));
            row.setEmpNo(emp == null ? "" : nvl(emp.getEmpNo()));
            row.setOrg(orgNames.getOrDefault(emp == null ? null : emp.getOrgId(), "未知组织"));
            row.setWorkDate(overtime.getWorkDate());
            row.setStartTime(overtime.getStartTime());
            row.setEndTime(overtime.getEndTime());
            row.setType(rowType);
            row.setHours(overtime.getHours());
            row.setEffectiveHours(overtime.getEffectiveHours());
            row.setStatus(overtime.getStatus());
            row.setSettleStatus(overtime.getSettleStatus());
            row.setSettledAt(overtime.getSettledAt());
            rows.add(row);
        }
        rows.sort(Comparator.comparing(OvertimeRow::getWorkDate).reversed()
                .thenComparing(OvertimeRow::getEmpNo).thenComparing(OvertimeRow::getId));
        Predicate<OvertimeRow> approved = x -> Integer.valueOf(1).equals(x.getStatus());
        Predicate<OvertimeRow> unsettled = approved.and(x -> Integer.valueOf(0).equals(x.getSettleStatus()));

        OvertimeLedger result = new OvertimeLedger();
        result.setOrgs(orgs);
        result.setApprovedHours(sumAmount(rows, approved, OvertimeRow::getHours));
        result.setEffectiveHours(sumAmount(rows,
                approved.and(x -> Integer.valueOf(1).equals(x.getSettleStatus())), OvertimeRow::getEffectiveHours));
        result.setUnsettledHours(sumAmount(rows, unsettled, OvertimeRow::getHours));
        result.setPendingCount(rows.stream().filter(x -> Integer.valueOf(0).equals(x.getStatus())).count());
        result.setMonthly(monthlyAmounts(rows, range, OvertimeRow::getWorkDate, approved, OvertimeRow::getHours));
        for (String key : OVERTIME_TYPES) {
            TypeAmount stat = new TypeAmount();
            stat.setKey(key);
            stat.setName(overtimeTypeName(key));
            stat.setValue(sumAmount(rows, approved.and(x -> key.equals(x.getType())), OvertimeRow::getHours));
            result.getTypes().add(stat);
        }
        rows.stream().collect(Collectors.groupingBy(OvertimeRow::getOrg, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    OvertimeOrgStat stat = new OvertimeOrgStat();
                    stat.setOrg(entry.getKey());
                    stat.setHours(sumAmount(entry.getValue(), approved, OvertimeRow::getHours));
                    stat.setUnsettledHours(sumAmount(entry.getValue(), unsettled, OvertimeRow::getHours));
                    result.getByOrg().add(stat);
                });
        result.setPage(page(rows, page, size));
        return result;
    }

    public RecruitmentLedger recruitment(String from, String to, Long orgId, String stage, String source,
                                         String keyword, int page, int size) {
        validatePage(page, size);
        DateRange range = dateRange(from, to);
        String normalizedStage = requireChoice(stage, Set.of("ALL", "RESUME", "INTERVIEW", "OFFER", "REJECTED"), "候选人阶段");
        String normalizedSource = source == null ? "" : source.trim();
        String normalizedKeyword = keyword(keyword);
        List<OrgOption> orgs = accessibleOrgs();
        Map<Long, String> orgNames = orgNames(orgs);
        LambdaQueryWrapper<Candidate> candidateQuery = Wrappers.lambdaQuery();
        dataScope.applyOrgScope(candidateQuery, Candidate::getOrgCode);
        List<Candidate> candidates = applyOrgFilter(candidateQuery, orgId, Candidate::getOrgId)
                ? candidateMapper.selectList(candidateQuery) : List.of();
        List<Candidate> filtered = candidates.stream()
                .filter(x -> "ALL".equals(normalizedStage) || normalizedStage.equals(x.getStage()))
                .filter(x -> normalizedSource.isEmpty() || normalizedSource.equalsIgnoreCase(nvl(x.getSource()).trim()))
                .filter(x -> matchesKeyword(x.getName(), x.getCandidateNo(), x.getPosition(), normalizedKeyword))
                .toList();
        Map<Long, Candidate> candidateMap = filtered.stream().filter(x -> x.getId() != null)
                .collect(Collectors.toMap(Candidate::getId, Function.identity(), (a, b) -> a));
        List<Long> candidateIds = List.copyOf(candidateMap.keySet());

        List<Offer> offers = new ArrayList<>();
        List<Interview> interviews = new ArrayList<>();
        if (!candidateIds.isEmpty()) {
            LambdaQueryWrapper<Offer> offerQuery = Wrappers.lambdaQuery();
            dataScope.applyOrgScope(offerQuery, Offer::getOrgCode);
            offers = offerMapper.selectList(offerQuery.in(Offer::getCandidateId, candidateIds));
            interviews = interviewMapper.selectList(Wrappers.<Interview>lambdaQuery()
                    .in(Interview::getCandidateId, candidateIds)
                    .eq(Interview::getStatus, Interview.STATUS_DONE)
                    .in(Interview::getRoundResult, Interview.RESULT_PASS, Interview.RESULT_FAIL)
                    .isNotNull(Interview::getDecidedAt));
        }
        Set<Long> scopedOfferIds = offers.stream().map(Offer::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        long recruitedEmployees = scopedEmployees(orgId).stream()
                .filter(x -> x.getSourceOfferId() != null && scopedOfferIds.contains(x.getSourceOfferId()))
                .filter(x -> inRange(x.getHireDate(), range.start(), range.end()))
                .map(Employee::getId).filter(Objects::nonNull).distinct().count();

        List<RecruitmentRow> rows = new ArrayList<>();
        for (Candidate candidate : filtered) {
            RecruitmentRow row = new RecruitmentRow();
            row.setId(idString(candidate.getId()));
            row.setCandidateId(idString(candidate.getId()));
            row.setCandidateNo(nvl(candidate.getCandidateNo()));
            row.setName(nvl(candidate.getName()));
            row.setOrg(orgNames.getOrDefault(candidate.getOrgId(), "未知组织"));
            row.setPosition(nvl(candidate.getPosition()));
            row.setSource(nvl(candidate.getSource()));
            row.setStage(candidate.getStage());
            row.setOwnerName(nvl(candidate.getOwnerName()));
            row.setAppliedAt(candidate.getAppliedAt());
            row.setLastStageAt(candidate.getLastStageAt());
            rows.add(row);
        }
        rows.sort(Comparator.comparing(RecruitmentRow::getAppliedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RecruitmentRow::getCandidateNo).thenComparing(RecruitmentRow::getId));

        RecruitmentLedger result = new RecruitmentLedger();
        result.setOrgs(orgs);
        result.setNewCandidates(filtered.stream().filter(x -> inRange(x.getCreatedAt(), range)).count());
        result.setInterviewedCandidates(interviews.stream()
                .filter(x -> inRange(x.getDecidedAt(), range))
                .map(Interview::getCandidateId).filter(Objects::nonNull).distinct().count());
        result.setConfirmedOffers(offers.stream().filter(x -> inRange(x.getConfirmedAt(), range)).count());
        result.setRecruitedEmployees(recruitedEmployees);
        Map<String, Long> sourceCounts = filtered.stream().collect(Collectors.groupingBy(
                x -> nvl(x.getSource()).trim().isEmpty() ? "UNKNOWN" : x.getSource().trim(),
                LinkedHashMap::new, Collectors.counting()));
        sourceCounts.forEach((key, value) -> result.getSources().add(countItem(key, recruitmentSourceName(key), value)));
        result.setStages(countItems(rows, List.of("RESUME", "INTERVIEW", "OFFER", "REJECTED"),
                RecruitmentRow::getStage, HrLedgerService::recruitmentStageName));
        Map<String, Long> positionCounts = filtered.stream().collect(Collectors.groupingBy(
                x -> nvl(x.getPosition()).trim().isEmpty() ? "未填写职位" : x.getPosition().trim(),
                LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> confirmedByPosition = new HashMap<>();
        offers.stream().filter(x -> inRange(x.getConfirmedAt(), range)).forEach(offer -> {
            Candidate candidate = candidateMap.get(offer.getCandidateId());
            if (candidate != null) {
                String position = nvl(candidate.getPosition()).trim().isEmpty() ? "未填写职位" : candidate.getPosition().trim();
                confirmedByPosition.merge(position, 1L, Long::sum);
            }
        });
        positionCounts.forEach((position, count) -> {
            RecruitmentPositionStat stat = new RecruitmentPositionStat();
            stat.setPosition(position);
            stat.setCandidates(count);
            stat.setConfirmedOffers(confirmedByPosition.getOrDefault(position, 0L));
            result.getPositions().add(stat);
        });
        result.setPage(page(rows, page, size));
        return result;
    }

    private static Map<Long, Employee> employeeMap(List<Employee> employees) {
        return employees.stream().filter(x -> x.getId() != null)
                .collect(Collectors.toMap(Employee::getId, Function.identity(), (a, b) -> a));
    }

    private static boolean activeEmployee(WorkforceRow row) {
        return Integer.valueOf(30).equals(row.getStatus()) || Integer.valueOf(40).equals(row.getStatus());
    }

    private static Integer age(LocalDate birthday, LocalDate today) {
        return birthday == null || birthday.isAfter(today) ? null : Period.between(birthday, today).getYears();
    }

    private static BigDecimal seniorityYears(Employee emp, LocalDate today) {
        if (emp.getSeniorityYears() != null) {
            return emp.getSeniorityYears();
        }
        return emp.getSeniorityStartDate() == null ? null
                : SeniorityService.yearsBetween(emp.getSeniorityStartDate(), today);
    }

    private static String educationName(String education, Map<String, String> names) {
        return education == null || education.isBlank() ? "未知" : names.getOrDefault(education, education);
    }

    private static List<CountItem> educationCountItems(List<WorkforceRow> rows, Map<String, String> names) {
        Map<String, Long> counts = rows.stream().collect(Collectors.groupingBy(
                x -> x.getEducation() == null || x.getEducation().isBlank() ? "UNKNOWN" : x.getEducation(),
                LinkedHashMap::new, Collectors.counting()));
        List<String> keys = new ArrayList<>();
        for (String key : names.keySet()) {
            if (counts.containsKey(key) && !"UNKNOWN".equals(key)) {
                keys.add(key);
            }
        }
        for (String key : counts.keySet()) {
            if (!"UNKNOWN".equals(key) && !keys.contains(key)) {
                keys.add(key);
            }
        }
        keys.add("UNKNOWN");
        List<CountItem> items = new ArrayList<>();
        for (String key : keys) {
            String name = "UNKNOWN".equals(key) ? "未知" : names.getOrDefault(key, key);
            items.add(countItem(key, name, counts.getOrDefault(key, 0L)));
        }
        return items;
    }

    private static String ageGroup(Integer age) {
        if (age == null) return "UNKNOWN";
        if (age < 25) return "<25";
        if (age < 30) return "25-29";
        if (age < 35) return "30-34";
        if (age < 40) return "35-39";
        if (age < 45) return "40-44";
        if (age < 50) return "45-49";
        return "50+";
    }

    private static String seniorityGroup(BigDecimal years) {
        if (years == null) return "UNKNOWN";
        if (years.compareTo(BigDecimal.ONE) < 0) return "<1";
        if (years.compareTo(BigDecimal.valueOf(3)) < 0) return "1-2";
        if (years.compareTo(BigDecimal.valueOf(5)) < 0) return "3-4";
        if (years.compareTo(BigDecimal.TEN) < 0) return "5-9";
        return "10+";
    }

    private static String groupName(String key) {
        return "UNKNOWN".equals(key) ? "未知" : key;
    }

    private static CountItem countItem(String key, String name, long value) {
        CountItem item = new CountItem();
        item.setKey(key);
        item.setName(name);
        item.setValue(value);
        return item;
    }

    private static boolean dueWithin30(RegularizationRow row) {
        return row.getRemainingDays() != null && row.getRemainingDays() >= 0 && row.getRemainingDays() <= 30;
    }

    private static boolean matchesRegularizationExpiry(Long remaining, String expiry) {
        return switch (expiry) {
            case "ALL" -> true;
            case "OVERDUE" -> remaining != null && remaining < 0;
            case "30" -> remaining != null && remaining >= 0 && remaining <= 30;
            case "60" -> remaining != null && remaining > 30 && remaining <= 60;
            case "LATER" -> remaining != null && remaining > 60;
            default -> false;
        };
    }

    private static String regularizeStatusName(String status) {
        return switch (status) {
            case "-1" -> "已驳回";
            case "0" -> "未申请";
            case "1" -> "部门审批中";
            case "2" -> "人事审批中";
            default -> status;
        };
    }

    private static boolean effectiveLeave(LeaveRow row) {
        return Integer.valueOf(1).equals(row.getStatus()) || Integer.valueOf(4).equals(row.getStatus());
    }

    private static String canonicalType(String value) {
        return value == null || value.isBlank() ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, String> configuredLeaveTypeNames() {
        Map<String, String> names = new LinkedHashMap<>(settingsClient.bizMap("leave_type"));
        if (names.isEmpty()) {
            names.putAll(DEFAULT_LEAVE_TYPES);
        }
        return names;
    }

    private static Map<String, String> defaultLeaveTypeNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("ANNUAL", "年假");
        names.put("SICK", "病假");
        names.put("PERSONAL", "事假");
        names.put("MARRIAGE", "婚假");
        names.put("MATERNITY", "产假");
        names.put("PATERNITY", "陪产假");
        names.put("COMPENSATORY", "调休");
        names.put("OTHER", "其他");
        return names;
    }

    private static String fallbackLeaveTypeName(String type) {
        String key = canonicalType(type);
        return DEFAULT_LEAVE_TYPES.getOrDefault(key, key);
    }

    private static String overtimeTypeName(String type) {
        return switch (type) {
            case "NORMAL" -> "工作日";
            case "RESTDAY" -> "休息日";
            case "HOLIDAY" -> "节假日";
            default -> type;
        };
    }

    private static <T> List<AmountMonth> monthlyAmounts(List<T> rows, DateRange range,
                                                        Function<T, LocalDate> dateFn,
                                                        Predicate<T> predicate,
                                                        Function<T, BigDecimal> valueFn) {
        List<AmountMonth> monthly = new ArrayList<>();
        YearMonth month = YearMonth.from(range.start());
        YearMonth end = YearMonth.from(range.end());
        while (!month.isAfter(end)) {
            YearMonth current = month;
            AmountMonth item = new AmountMonth();
            item.setMonth(month.toString());
            item.setValue(sumAmount(rows, x -> predicate.test(x) && dateFn.apply(x) != null
                    && YearMonth.from(dateFn.apply(x)).equals(current), valueFn));
            monthly.add(item);
            month = month.plusMonths(1);
        }
        return monthly;
    }

    private static <T> BigDecimal sumAmount(List<T> rows, Predicate<T> predicate,
                                            Function<T, BigDecimal> valueFn) {
        BigDecimal total = BigDecimal.ZERO;
        for (T row : rows) {
            if (predicate.test(row)) {
                total = total.add(amount(valueFn.apply(row)));
            }
        }
        return total;
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean matchesKeyword(String name, String empNo, String documentNo, String keyword) {
        return keyword.isEmpty() || (nvl(name) + nvl(empNo) + nvl(documentNo))
                .toLowerCase(Locale.ROOT).contains(keyword);
    }

    private static void validatePage(int page, int size) {
        if (page < 1 || size < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "页码和每页条数必须大于 0");
        }
    }

    private static Integer parseIntFilter(String raw, Set<Integer> allowed, String label) {
        String value = normalize(raw);
        if ("ALL".equals(value)) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (!allowed.contains(parsed)) {
                throw new BizException(ErrorCode.PARAM_ERROR, label + "不合法");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, label + "不合法");
        }
    }

    private static String requireChoice(String raw, Set<String> allowed, String label) {
        String value = normalize(raw);
        if (!allowed.contains(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR, label + "不合法");
        }
        return value;
    }

    private static DateRange dateRange(String fromRaw, String toRaw) {
        YearMonth current = YearMonth.now();
        LocalDate from = parseDate(fromRaw, current.minusMonths(11).atDay(1));
        LocalDate to = parseDate(toRaw, current.atEndOfMonth());
        if (to.isBefore(from)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }
        if (ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) > 119) {
            throw new BizException(ErrorCode.PARAM_ERROR, "日期跨度不能超过 120 个月");
        }
        return new DateRange(from, to);
    }

    private static LocalDate parseDate(String raw, LocalDate defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "日期格式应为 yyyy-MM-dd");
        }
    }

    private static String idString(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }

    private ContractLedger emptyContract(List<OrgOption> orgs, int page, int size) {
        ContractLedger result = new ContractLedger();
        result.setOrgs(orgs);
        result.setPage(page(List.of(), page, size));
        return result;
    }

    private AttendanceLedger emptyAttendance(List<OrgOption> orgs, int page, int size) {
        AttendanceLedger result = new AttendanceLedger();
        result.setOrgs(orgs);
        result.setPage(page(List.of(), page, size));
        return result;
    }

    private List<OrgOption> accessibleOrgs() {
        return accessibleOrgs(null);
    }

    private List<OrgOption> accessibleOrgs(String teamOrgCode) {
        LambdaQueryWrapper<OrgNode> w = Wrappers.lambdaQuery();
        dataScope.applyOrgScope(w, OrgNode::getCode);
        if (teamOrgCode != null) {
            w.likeRight(OrgNode::getCode, teamOrgCode);
        }
        w.orderByAsc(OrgNode::getSort).orderByAsc(OrgNode::getId);
        return orgMapper.selectList(w).stream().map(org -> {
            OrgOption option = new OrgOption();
            option.setId(org.getId());
            option.setParentId(org.getParentId());
            option.setName(nvl(org.getShortName()).isBlank() ? nvl(org.getName()) : org.getShortName());
            return option;
        }).toList();
    }

    private Map<Long, String> orgNames(List<OrgOption> orgs) {
        return orgs.stream().collect(Collectors.toMap(OrgOption::getId, OrgOption::getName));
    }

    private List<Employee> scopedEmployees(Long orgId) {
        return scopedEmployees(orgId, null);
    }

    private List<Employee> scopedEmployees(Long orgId, String teamOrgCode) {
        LambdaQueryWrapper<Employee> w = Wrappers.lambdaQuery();
        dataScope.applyOrgScope(w, Employee::getOrgCode);
        if (teamOrgCode != null) {
            w.likeRight(Employee::getOrgCode, teamOrgCode);
        }
        if (!applyOrgFilter(w, orgId, Employee::getOrgId)) {
            return List.of();
        }
        return employeeMapper.selectList(w);
    }

    private List<Long> scopedEmployeeIds(Long orgId) {
        return scopedEmployees(orgId).stream().map(Employee::getId).filter(Objects::nonNull).toList();
    }

    private <T> boolean applyOrgFilter(LambdaQueryWrapper<T> w, Long orgId,
                                       com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> orgIdFn) {
        if (orgId == null) {
            return true;
        }
        List<Long> ids = subtreeIds(orgId);
        if (ids.isEmpty()) {
            return false;
        }
        w.in(orgIdFn, ids);
        return true;
    }

    private List<Long> subtreeIds(Long rootId) {
        OrgNode root = orgMapper.selectById(rootId);
        if (root == null) {
            return List.of();
        }
        if (root.getPath() == null || root.getPath().isBlank()) {
            return List.of(rootId);
        }
        List<OrgNode> nodes = orgMapper.selectList(Wrappers.<OrgNode>lambdaQuery()
                .likeRight(OrgNode::getPath, root.getPath()));
        return nodes.isEmpty() ? List.of(rootId) : nodes.stream().map(OrgNode::getId).toList();
    }

    private Map<Long, Employee> employeesOf(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return employeeMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity(), (a, b) -> a));
    }

    private MovementRow movementRow(Employee emp, LocalDate date, String event, String detail,
                                    Map<Long, String> orgNames) {
        MovementRow row = new MovementRow();
        row.setId(emp.getId() + "-" + event);
        row.setEmployeeId(emp.getId());
        row.setDate(date);
        row.setName(nvl(emp.getName()));
        row.setEmpNo(nvl(emp.getEmpNo()));
        row.setOrg(orgNames.getOrDefault(emp.getOrgId(), "未知组织"));
        row.setEvent(event);
        row.setDetail(detail);
        return row;
    }

    private AttendanceRow attendanceRow(AttendanceDaily daily, Employee emp, String type, double value,
                                        Map<Long, String> orgNames) {
        AttendanceRow row = new AttendanceRow();
        row.setId(daily.getId() + "-" + type);
        row.setEmployeeId(daily.getEmployeeId());
        row.setDate(daily.getWorkDate());
        row.setName(emp == null ? "" : nvl(emp.getName()));
        row.setEmpNo(emp == null ? "" : nvl(emp.getEmpNo()));
        row.setOrg(orgNames.getOrDefault(emp == null ? null : emp.getOrgId(), "未知组织"));
        row.setType(type);
        row.setValue(value);
        row.setStatus(Integer.valueOf(1).equals(daily.getCorrectionStatus()) ? "PENDING"
                : Integer.valueOf(2).equals(daily.getCorrectionStatus()) ? "DONE" : "NONE");
        return row;
    }

    private double turnoverRate(List<Employee> employees, LocalDate start, LocalDate end, long resigns) {
        long beginning = employees.stream().filter(x -> workingAt(x, start)).count();
        long ending = employees.stream().filter(x -> workingAt(x, end)).count();
        double average = (beginning + ending) / 2.0;
        if (average <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(resigns * 100.0 / average).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private boolean workingAt(Employee emp, LocalDate date) {
        return hasEntered(emp) && emp.getHireDate() != null && !emp.getHireDate().isAfter(date)
                && (emp.getResignDate() == null || emp.getResignDate().isAfter(date));
    }

    private boolean hasEntered(Employee emp) {
        return emp.getStatus() != null && emp.getStatus() >= 30;
    }

    private boolean matchesExpiry(long remaining, String expiry) {
        return switch (expiry) {
            case "30" -> remaining >= 0 && remaining <= 30;
            case "60" -> remaining > 30 && remaining <= 60;
            case "90" -> remaining > 60 && remaining <= 90;
            case "EXPIRED" -> remaining < 0;
            default -> true;
        };
    }

    private boolean matchesContractKeyword(Contract c, Employee emp, String keyword) {
        if (keyword.isEmpty()) {
            return true;
        }
        String text = (emp == null ? "" : nvl(emp.getName()) + nvl(emp.getEmpNo())) + nvl(c.getContractNo());
        return text.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private static long countAttendance(List<AttendanceRow> rows, String type) {
        return rows.stream().filter(x -> type.equals(x.getType())).count();
    }

    private static long attendanceValue(List<AttendanceRow> rows, String type) {
        return rows.stream().filter(x -> type.equals(x.getType())).mapToLong(x -> Math.round(x.getValue())).sum();
    }

    private static <T> List<CountItem> countItems(List<T> rows, List<String> keys,
                                                  Function<T, String> keyFn, Function<String, String> nameFn) {
        List<CountItem> items = new ArrayList<>();
        for (String key : keys) {
            CountItem item = new CountItem();
            item.setKey(key);
            item.setName(nameFn.apply(key));
            item.setValue(rows.stream().filter(x -> key.equals(keyFn.apply(x))).count());
            items.add(item);
        }
        return items;
    }

    private static <T> R.Page<T> page(List<T> rows, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int from = (int) Math.min((long) (safePage - 1) * safeSize, rows.size());
        int to = Math.min(from + safeSize, rows.size());
        return R.Page.of(rows.subList(from, to), rows.size());
    }

    private static YearMonth parseMonth(String value, YearMonth defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "月份格式应为 yyyy-MM");
        }
    }

    private static boolean inRange(LocalDate value, LocalDate start, LocalDate end) {
        return value != null && !value.isBefore(start) && !value.isAfter(end);
    }

    private static boolean inRange(LocalDateTime value, DateRange range) {
        return value != null && !value.isBefore(range.start().atStartOfDay())
                && value.isBefore(range.end().plusDays(1).atStartOfDay());
    }

    private static String recruitmentSourceName(String source) {
        return "UNKNOWN".equals(source) ? "未知来源" : source;
    }

    private static String recruitmentStageName(String stage) {
        return switch (stage) {
            case "RESUME" -> "简历筛选";
            case "INTERVIEW" -> "面试中";
            case "OFFER" -> "Offer 阶段";
            case "REJECTED" -> "已淘汰";
            default -> stage;
        };
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "ALL" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String keyword(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static int number(Integer value) {
        return value == null ? 0 : value;
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    private static String contractTypeName(String type) {
        return switch (type) {
            case "FIXED" -> "固定期限";
            case "UNFIXED" -> "无固定期限";
            case "INTERN" -> "实习协议";
            case "LABOR" -> "劳务协议";
            default -> type;
        };
    }

    private static String attendanceTypeName(String type) {
        return switch (type) {
            case "LATE" -> "迟到";
            case "EARLY" -> "早退";
            case "MISSING" -> "缺卡";
            case "ABSENT" -> "缺勤";
            default -> type;
        };
    }
}
