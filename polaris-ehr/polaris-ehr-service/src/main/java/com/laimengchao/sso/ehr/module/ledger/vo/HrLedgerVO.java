package com.laimengchao.sso.ehr.module.ledger.vo;

import com.laimengchao.sso.common.R;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 人力台账只读视图：汇总、图表和分页明细共享同一筛选结果。 */
public class HrLedgerVO {

    @Data
    public static class OrgOption {
        private Long id;
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
}
