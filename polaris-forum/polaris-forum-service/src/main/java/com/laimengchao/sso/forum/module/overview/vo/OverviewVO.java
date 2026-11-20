package com.laimengchao.sso.forum.module.overview.vo;

import lombok.Data;

import java.util.List;

/**
 * 论坛管理端「北极星核」概览聚合统计。
 * <p>
 * 一次请求返回概览页所需的全部数字与图表数据，避免前端拼多个接口：
 * 规模数字（内容/互动/成员）+ 近 14 天内容趋势 + 一级板块内容分布 + 成员等级分布。
 * 分布项用（名称, 数量）二元组，前端据此渲染条形/环形图，不依赖任何图表库。
 */
@Data
public class OverviewVO {

    /* ---------- 规模数字卡 ---------- */
    /** 帖子总数（deleted=0） */
    private long threadCount;
    /** 评论总数（deleted=0） */
    private long commentCount;
    /** 注册成员数 */
    private long memberCount;
    /** 累计获赞数（forum_like 记录数） */
    private long likeCount;
    /** 帖子累计浏览量（views_count 之和） */
    private long viewCount;
    /** 待处理举报数 */
    private long pendingReportCount;

    /* ---------- 次级规模卡 ---------- */
    /** 一级板块数（parent_key 为空且启用） */
    private long boardCount;
    /** 内容标签数 */
    private long tagCount;
    /** 今日新增帖子数 */
    private long todayThreadCount;
    /** 本月新增帖子数 */
    private long monthThreadCount;
    /** 敏感词库数量 */
    private long sensitiveWordCount;
    /** 已发放勋章数（成员持有） */
    private long badgeAwardCount;

    /* ---------- 图表数据 ---------- */
    /** 近 14 天内容趋势，按日期升序，缺勤日补 0 */
    private List<TrendItem> contentTrend;
    /** 一级板块内容分布，按帖子数降序 */
    private List<DistributionItem> boardDistribution;
    /** 成员等级分布，按等级序号升序 */
    private List<DistributionItem> levelDistribution;

    @Data
    public static class TrendItem {
        /** 日期 yyyy-MM-dd */
        private String date;
        /** 当日新增帖子数 */
        private long threads;
        /** 当日新增评论数 */
        private long comments;

        public TrendItem() {
        }

        public TrendItem(String date, long threads, long comments) {
            this.date = date;
            this.threads = threads;
            this.comments = comments;
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
