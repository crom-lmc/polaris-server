package com.laimengchao.sso.account.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 账号中心「概览」聚合统计。
 * <p>
 * 一次请求返回概览页所需的全部数字与图表数据，避免前端拼多个接口：
 * 数字卡（账号/应用/角色/权限/今日登录/本月新增）+ 近 14 天登录趋势 + 账号状态分布 + 登录方式分布。
 * 有数据权限范围时，六张卡片与三张图共享同一批范围内账号，口径与账户管理列表一致。
 * 分布项用（名称, 数量）二元组，前端据此渲染环形/柱状图，不依赖任何图表库。
 */
@Data
public class OverviewVO {

    /** 有效账号数（deleted=0） */
    private long accountCount;
    /** 启用应用数 */
    private long appCount;
    /** 角色数 */
    private long roleCount;
    /** 权限码数 */
    private long permissionCount;
    /** 今日登录次数（成功+失败） */
    private long todayLoginCount;
    /** 本月新增账号数 */
    private long monthNewAccountCount;

    /** 近 14 天登录趋势，按日期升序，缺勤日补 0 */
    private List<LoginTrendItem> loginTrend;
    /** 账号状态分布：正常/禁用/锁定/未激活 */
    private List<DistributionItem> accountStatus;
    /** 登录方式分布：password/email/wechat 等 */
    private List<DistributionItem> loginTypes;

    @Data
    public static class LoginTrendItem {
        /** 日期 yyyy-MM-dd */
        private String date;
        /** 成功次数 */
        private long success;
        /** 失败次数 */
        private long fail;

        public LoginTrendItem() {
        }

        public LoginTrendItem(String date, long success, long fail) {
            this.date = date;
            this.success = success;
            this.fail = fail;
        }
    }

    @Data
    public static class DistributionItem {
        private String name;
        private long value;

        public DistributionItem() {
        }

        public DistributionItem(String name, long value) {
            this.name = name;
            this.value = value;
        }
    }
}
