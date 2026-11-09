package com.laimengchao.sso.ehr.module.dashboard.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 人力看板汇总。所有口径都基于 {@code DataScopeService} 过滤后的员工集合，
 * 保证卡片数字与人员名录的筛选结果一致。
 */
@Data
public class DashboardSummaryVO {

    private Overview overview = new Overview();
    /** 部门人力分布：按员工直属组织聚合（只含试用 / 在职） */
    private List<OrgDistribution> orgDistribution = new ArrayList<>();
    /** 编制 vs 实有：只列配置了编制或已有在职人员的组织 */
    private List<HeadcountItem> headcount = new ArrayList<>();
    private HeadcountTotal headcountTotal = new HeadcountTotal();
    /** 请假趋势：按「月份 + 类型」展开，前端做堆叠柱状 / 折线 */
    private List<LeaveTrend> leaveTrend = new ArrayList<>();

    @Data
    public static class Overview {
        /** 正式在职（status=40） */
        private long active;
        /** 试用期（status=30） */
        private long probation;
        /** 待入职（status=10/20/25） */
        private long onboarding;
        /** 离职中（status=50） */
        private long terminating;
        /** 本月入职：入职日期落在本月且已入职 */
        private long onboardThisMonth;
        /** 本月离职：离职日期落在本月且已离职 */
        private long resignThisMonth;
        /** 本月转正：预计转正日期落在本月且已转正式 */
        private long regularizedThisMonth;
    }

    @Data
    public static class OrgDistribution {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long orgId;
        private String orgName;
        private String type;
        private long employees;
    }

    @Data
    public static class HeadcountItem {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long orgId;
        private String orgName;
        private String type;
        /** 编制人数，未配置为 0 */
        private int headcount;
        /** 实有人数：直属试用 + 在职 */
        private long actual;
        /** 利用率 = 实有 / 编制 × 100；编制为 0 时为 null，前端显示「—」 */
        private Double utilization;
    }

    @Data
    public static class HeadcountTotal {
        private long headcount;
        private long actual;
        private Double utilization;
    }

    @Data
    public static class LeaveTrend {
        /** yyyy-MM */
        private String month;
        private String leaveType;
        private String leaveTypeName;
        /** 已通过请假的天数合计 */
        private double days;
    }
}
