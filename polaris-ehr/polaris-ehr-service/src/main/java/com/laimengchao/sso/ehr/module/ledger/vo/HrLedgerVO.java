package com.laimengchao.sso.ehr.module.ledger.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.laimengchao.sso.common.R;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** 人力台账只读视图：汇总、图表和分页明细共享同一筛选结果。 */
public class HrLedgerVO {

    @Data
    public static class OrgOption {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long parentId;
        private String name;
    }

    @Data
    public static class CountItem {
        private String key;
        private String name;
        private long value;
    }

    @Data
    public static class MonthCount {
        private String month;
        private long value;
    }

    @Data
    public static class DailyCount {
        private String date;
        private long value;
    }

    @Data
    public static class ContractRow {
        private String id;
        private Long employeeId;
        private String name;
        private String empNo;
        private String org;
        private String contractNo;
        private String type;
        private LocalDate startDate;
        private LocalDate endDate;
        private long remainingDays;
        private String status;
    }

    @Data
    public static class ContractLedger {
        private R.Page<ContractRow> page;
        private long within30;
        private long within60;
        private long within90;
        private long expired;
        private List<OrgOption> orgs = new ArrayList<>();
        private List<MonthCount> monthly = new ArrayList<>();
        private List<CountItem> types = new ArrayList<>();
    }

    @Data
    public static class MovementRow {
        private String id;
        private Long employeeId;
        private LocalDate date;
        private String name;
        private String empNo;
        private String org;
        private String event;
        private String detail;
    }

    @Data
    public static class MovementMonth {
        private String month;
        private long hires;
        private long resigns;
    }

    @Data
    public static class OrgNet {
        private String org;
        private long value;
    }

    @Data
    public static class MovementLedger {
        private R.Page<MovementRow> page;
        private long hires;
        private long resigns;
        private long net;
        private double turnoverRate;
        private List<OrgOption> orgs = new ArrayList<>();
        private List<MovementMonth> monthly = new ArrayList<>();
        private List<OrgNet> orgNet = new ArrayList<>();
    }

    @Data
    public static class AttendanceRow {
        private String id;
        private Long employeeId;
        private LocalDate date;
        private String name;
        private String empNo;
        private String org;
        private String type;
        private double value;
        private String status;
    }

    @Data
    public static class AttendanceLedger {
        private R.Page<AttendanceRow> page;
        private long total;
        private long late;
        private long early;
        private long missing;
        private long absent;
        private long lateMinutes;
        private long earlyMinutes;
        private List<OrgOption> orgs = new ArrayList<>();
        private List<DailyCount> daily = new ArrayList<>();
        private List<CountItem> types = new ArrayList<>();
    }

    @Data
    public static class WorkforceRow {
        private String id;
        private String employeeId;
        private String name;
        private String empNo;
        private String org;
        private String position;
        private Integer status;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate hireDate;
        private Integer age;
        private String education;
        private String educationName;
        private BigDecimal seniorityYears;
    }

    @Data
    public static class WorkforceLedger {
        private R.Page<WorkforceRow> page;
        private long active;
        private long probation;
        private long pending;
        private long offboarding;
        private List<OrgOption> orgs = new ArrayList<>();
        private List<CountItem> byOrg = new ArrayList<>();
        private List<CountItem> educations = new ArrayList<>();
        private List<CountItem> ageGroups = new ArrayList<>();
        private List<CountItem> seniorityGroups = new ArrayList<>();
    }

    @Data
    public static class RegularizationRow {
        private String id;
        private String employeeId;
        private String name;
        private String empNo;
        private String org;
        private String position;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate hireDate;
        private Integer probationMonths;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate expectedRegularizeDate;
        private Long remainingDays;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate lastApplyDate;
        private Integer approvalStatus;
    }

    @Data
    public static class RegularizeOrgStat {
        private String org;
        private long overdue;
        private long due30;
    }

    @Data
    public static class RegularizationLedger {
        private R.Page<RegularizationRow> page;
        private long current;
        private long overdue;
        private long due30;
        private long approving;
        private List<OrgOption> orgs = new ArrayList<>();
        private List<MonthCount> monthly = new ArrayList<>();
        private List<RegularizeOrgStat> byOrg = new ArrayList<>();
        private List<CountItem> approvalStatuses = new ArrayList<>();
    }

    @Data
    public static class LeaveRow {
        private String id;
        private String employeeId;
        private String leaveNo;
        private String name;
        private String empNo;
        private String org;
        private String type;
        private String typeName;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate startDate;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate endDate;
        private BigDecimal days;
        private String dayMode;
        private Integer status;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate returnDate;
    }

    @Data
    public static class LeaveTypeStat {
        private String key;
        private String name;
        private long count;
        private BigDecimal days = BigDecimal.ZERO;
    }

    @Data
    public static class OrgAmount {
        private String org;
        private BigDecimal value = BigDecimal.ZERO;
    }

    @Data
    public static class AmountMonth {
        private String month;
        private BigDecimal value = BigDecimal.ZERO;
    }

    @Data
    public static class LeaveLedger {
        private R.Page<LeaveRow> page;
        private long effectivePeople;
        private BigDecimal effectiveDays = BigDecimal.ZERO;
        private BigDecimal pendingDays = BigDecimal.ZERO;
        private long pendingCount;
        private List<OrgOption> orgs = new ArrayList<>();
        private List<AmountMonth> monthly = new ArrayList<>();
        private List<LeaveTypeStat> types = new ArrayList<>();
        private List<OrgAmount> byOrg = new ArrayList<>();
    }

    @Data
    public static class OvertimeRow {
        private String id;
        private String employeeId;
        private String overtimeNo;
        private String name;
        private String empNo;
        private String org;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate workDate;
        private LocalTime startTime;
        private LocalTime endTime;
        private String type;
        private BigDecimal hours;
        private BigDecimal effectiveHours;
        private Integer status;
        private Integer settleStatus;
        private LocalDateTime settledAt;
    }

    @Data
    public static class OvertimeOrgStat {
        private String org;
        private BigDecimal hours = BigDecimal.ZERO;
        private BigDecimal unsettledHours = BigDecimal.ZERO;
    }

    @Data
    public static class TypeAmount {
        private String key;
        private String name;
        private BigDecimal value = BigDecimal.ZERO;
    }

    @Data
    public static class OvertimeLedger {
        private R.Page<OvertimeRow> page;
        private BigDecimal approvedHours = BigDecimal.ZERO;
        private BigDecimal effectiveHours = BigDecimal.ZERO;
        private BigDecimal unsettledHours = BigDecimal.ZERO;
        private long pendingCount;
        private List<OrgOption> orgs = new ArrayList<>();
        private List<AmountMonth> monthly = new ArrayList<>();
        private List<TypeAmount> types = new ArrayList<>();
        private List<OvertimeOrgStat> byOrg = new ArrayList<>();
    }

    @Data
    public static class RecruitmentRow {
        private String id;
        private String candidateId;
        private String candidateNo;
        private String name;
        private String org;
        private String position;
        private String source;
        private String stage;
        private String ownerName;
        private LocalDateTime appliedAt;
        private LocalDateTime lastStageAt;
    }

    @Data
    public static class RecruitmentPositionStat {
        private String position;
        private long candidates;
        private long confirmedOffers;
    }

    @Data
    public static class RecruitmentLedger {
        private R.Page<RecruitmentRow> page;
        private long newCandidates;
        private long interviewedCandidates;
        private long confirmedOffers;
        private long recruitedEmployees;
        private List<OrgOption> orgs = new ArrayList<>();
        private List<CountItem> sources = new ArrayList<>();
        private List<CountItem> stages = new ArrayList<>();
        private List<RecruitmentPositionStat> positions = new ArrayList<>();
    }

}
