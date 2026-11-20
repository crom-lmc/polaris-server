package com.laimengchao.sso.forum.module.overview.service.impl;

import com.laimengchao.sso.forum.module.overview.mapper.OverviewMapper;
import com.laimengchao.sso.forum.module.overview.service.OverviewService;
import com.laimengchao.sso.forum.module.overview.vo.OverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 论坛管理端概览统计实现：把聚合查询结果补齐为前端可直接渲染的结构。
 */
@Service
@RequiredArgsConstructor
public class OverviewServiceImpl implements OverviewService {

    private static final int TREND_DAYS = 14;
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final OverviewMapper mapper;

    /**
     * 组织在册人数（未离职员工）。论坛成员是它的子集，没进论坛的人按经验 0 计入 Lv.1，
     * 使「社区成员」卡片与等级分布合计都对齐组织人数。配置为 0 时不补齐，保持真实注册数。
     */
    @Value("${forum.overview.org-member-count:0}")
    private long orgMemberCount;

    @Override
    public OverviewVO overview() {
        OverviewVO vo = new OverviewVO();
        long registered = mapper.countMembers();
        vo.setThreadCount(mapper.countThreads());
        vo.setCommentCount(mapper.countComments());
        // 社区成员数按组织在册口径展示，未进论坛的组织成员也算社区一员
        vo.setMemberCount(orgMemberCount > registered ? orgMemberCount : registered);
        vo.setLikeCount(mapper.countLikes());
        vo.setViewCount(mapper.sumViews());
        vo.setPendingReportCount(mapper.countPendingReports());
        vo.setBoardCount(mapper.countBoards());
        vo.setTagCount(mapper.countTags());
        vo.setTodayThreadCount(mapper.countTodayThreads());
        vo.setMonthThreadCount(mapper.countMonthThreads());
        vo.setSensitiveWordCount(mapper.countSensitiveWords());
        vo.setBadgeAwardCount(mapper.countBadgeAwards());
        vo.setContentTrend(buildTrend());
        vo.setBoardDistribution(buildDistribution(mapper.boardDistribution(), false));
        vo.setLevelDistribution(buildLevelDistribution(registered));
        return vo;
    }

    /**
     * 成员等级分布：先取真实注册成员的等级分布，再把「组织在册人数 − 已注册成员数」的差额
     * 按经验 0 补进 Lv.1（分布首项即最低等级）。这样分布合计 = 社区成员卡片 = 组织人数。
     */
    private List<OverviewVO.DistributionItem> buildLevelDistribution(long registered) {
        List<OverviewVO.DistributionItem> items = buildDistribution(mapper.levelDistribution(), true);
        if (orgMemberCount > registered && !items.isEmpty()) {
            OverviewVO.DistributionItem first = items.get(0);
            first.setValue(first.getValue() + (orgMemberCount - registered));
        }
        return items;
    }

    /** 近 14 天按日期升序，帖子/评论各自缺勤日补 0 */
    private List<OverviewVO.TrendItem> buildTrend() {
        Map<String, Long> threadByDay = countByDay(mapper.threadTrend(TREND_DAYS));
        Map<String, Long> commentByDay = countByDay(mapper.commentTrend(TREND_DAYS));
        List<OverviewVO.TrendItem> items = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            String day = today.minusDays(i).format(DAY_FORMAT);
            items.add(new OverviewVO.TrendItem(
                    day,
                    threadByDay.getOrDefault(day, 0L),
                    commentByDay.getOrDefault(day, 0L)));
        }
        return items;
    }

    private Map<String, Long> countByDay(List<Map<String, Object>> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String day = String.valueOf(row.get("day"));
            result.put(day, number(row.get("cnt")));
        }
        return result;
    }

    /** 分组行 -> 分布项；keepZero=true 时保留 0 值（板块要显示空板块） */
    private List<OverviewVO.DistributionItem> buildDistribution(
            List<Map<String, Object>> rows, boolean keepZero) {
        List<OverviewVO.DistributionItem> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String name = row.get("name") == null ? "未分类" : String.valueOf(row.get("name"));
            long cnt = number(row.get("cnt"));
            if (!keepZero && cnt <= 0) continue;
            items.add(new OverviewVO.DistributionItem(name, cnt));
        }
        return items;
    }

    private long number(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value == null) return 0;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
