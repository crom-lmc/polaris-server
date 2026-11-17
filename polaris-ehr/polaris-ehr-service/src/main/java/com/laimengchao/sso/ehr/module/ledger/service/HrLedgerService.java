package com.laimengchao.sso.ehr.module.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceDaily;
import com.laimengchao.sso.ehr.module.attendance.mapper.AttendanceDailyMapper;
import com.laimengchao.sso.ehr.module.contract.entity.Contract;
import com.laimengchao.sso.ehr.module.contract.mapper.ContractMapper;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.AttendanceLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.AttendanceRow;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.ContractLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.ContractRow;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.CountItem;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.DailyCount;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.MonthCount;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.MovementLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.MovementMonth;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.MovementRow;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.OrgNet;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.OrgOption;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import com.laimengchao.sso.ehr.security.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 人力台账查询。所有业务查询均先叠加组织数据权限，再做用户筛选。 */
@Service
@RequiredArgsConstructor
public class HrLedgerService {

    private static final List<String> CONTRACT_TYPES = List.of("FIXED", "UNFIXED", "INTERN", "LABOR");
    private static final List<String> ATTENDANCE_TYPES = List.of("LATE", "EARLY", "MISSING", "ABSENT");

    private final ContractMapper contractMapper;
    private final EmployeeMapper employeeMapper;
    private final AttendanceDailyMapper attendanceMapper;
    private final OrgNodeMapper orgMapper;
    private final DataScopeService dataScope;

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
        LambdaQueryWrapper<OrgNode> w = Wrappers.lambdaQuery();
        dataScope.applyOrgScope(w, OrgNode::getCode);
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
        LambdaQueryWrapper<Employee> w = Wrappers.lambdaQuery();
        dataScope.applyOrgScope(w, Employee::getOrgCode);
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
