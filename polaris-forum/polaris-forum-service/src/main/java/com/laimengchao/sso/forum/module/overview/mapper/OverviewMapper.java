package com.laimengchao.sso.forum.module.overview.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 论坛管理端概览统计聚合查询。
 * <p>
 * 只读统计，集中在同一 Mapper 便于维护口径；数字用 count/sum，趋势/分布用 group by。
 * 内容口径统一排除 deleted=1，时间口径统一用 created_at。
 */
public interface OverviewMapper {

    @Select("SELECT COUNT(1) FROM forum_thread WHERE deleted = 0")
    long countThreads();

    @Select("SELECT COUNT(1) FROM forum_comment WHERE deleted = 0")
    long countComments();

    @Select("SELECT COUNT(1) FROM forum_member")
    long countMembers();

    @Select("SELECT COUNT(1) FROM forum_like")
    long countLikes();

    @Select("SELECT COALESCE(SUM(views_count),0) FROM forum_thread WHERE deleted = 0")
    long sumViews();

    @Select("SELECT COUNT(1) FROM forum_report WHERE status = 'PENDING'")
    long countPendingReports();

    /** 一级板块数：parent_key 为空且启用 */
    @Select("SELECT COUNT(1) FROM forum_board WHERE (parent_key IS NULL OR parent_key = '') AND enabled = 1")
    long countBoards();

    @Select("SELECT COUNT(1) FROM forum_tag")
    long countTags();

    @Select("SELECT COUNT(1) FROM forum_thread WHERE deleted = 0 AND created_at >= CURDATE()")
    long countTodayThreads();

    @Select("SELECT COUNT(1) FROM forum_thread WHERE deleted = 0 AND created_at >= DATE_FORMAT(CURDATE(), '%Y-%m-01')")
    long countMonthThreads();

    @Select("SELECT COUNT(1) FROM forum_sensitive_word")
    long countSensitiveWords();

    @Select("SELECT COUNT(1) FROM forum_member_badge")
    long countBadgeAwards();

    /**
     * 近 N 天内容趋势：按日期聚合新增帖子/评论数。
     * <p>
     * 分别取两个表的日期分组，缺勤日期由 Service 层补 0，保证图表横轴连续。
     */
    @Select("""
            SELECT DATE(created_at) AS day, COUNT(1) AS cnt
            FROM forum_thread
            WHERE deleted = 0 AND created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} - 1 DAY)
            GROUP BY DATE(created_at)
            """)
    List<Map<String, Object>> threadTrend(@Param("days") int days);

    @Select("""
            SELECT DATE(created_at) AS day, COUNT(1) AS cnt
            FROM forum_comment
            WHERE deleted = 0 AND created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} - 1 DAY)
            GROUP BY DATE(created_at)
            """)
    List<Map<String, Object>> commentTrend(@Param("days") int days);

    /** 一级板块内容分布：按一级板块聚合帖子数（含 0 的板块保留，前端可过滤） */
    @Select("""
            SELECT b.name AS name, COUNT(t.id) AS cnt
            FROM forum_board b
            LEFT JOIN forum_thread t ON t.board_key = b.board_key AND t.deleted = 0
            WHERE (b.parent_key IS NULL OR b.parent_key = '') AND b.enabled = 1
            GROUP BY b.board_key, b.name
            ORDER BY cnt DESC, b.sort_no ASC
            """)
    List<Map<String, Object>> boardDistribution();

    /**
     * 成员等级分布：按经验归入其所属等级（取不超过该成员经验的最高门槛等级）。
     * <p>
     * 与 LevelService.progress 口径一致：经验落在 [minExperience, 下一等级 minExperience) 即该级。
     */
    @Select("""
            SELECT l.level_no AS `no`, l.name AS name, COUNT(m.account_id) AS cnt
            FROM forum_level l
            LEFT JOIN forum_member m
              ON m.experience >= l.min_experience
             AND NOT EXISTS (
                   SELECT 1 FROM forum_level l2
                   WHERE l2.enabled = 1
                     AND l2.min_experience > l.min_experience
                     AND l2.min_experience <= m.experience
             )
            WHERE l.enabled = 1
            GROUP BY l.id, l.level_no, l.name
            ORDER BY l.level_no
            """)
    List<Map<String, Object>> levelDistribution();
}
