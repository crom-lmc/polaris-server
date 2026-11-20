package com.laimengchao.sso.account.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 账号中心概览统计聚合查询。
 * <p>
 * 只读统计，集中在同一 Mapper 便于维护口径；数字用 count、趋势/分布用 group by。
 * 时间口径统一用 created_at，与登录日志、账号表一致。
 */
public interface OverviewMapper {

    /** 有效账号数 */
    @Select("SELECT COUNT(1) FROM sso_account WHERE deleted = 0")
    long countAccounts();

    /** 启用应用数 */
    @Select("SELECT COUNT(1) FROM sso_application WHERE status = 1 AND deleted = 0")
    long countApps();

    /** 角色数 */
    @Select("SELECT COUNT(1) FROM sso_role WHERE deleted = 0")
    long countRoles();

    /** 权限码数 */
    @Select("SELECT COUNT(1) FROM sso_permission WHERE deleted = 0")
    long countPermissions();

    /** 今日登录次数（成功+失败），按服务器当天 00:00 起算 */
    @Select("SELECT COUNT(1) FROM sso_login_log WHERE created_at >= CURDATE()")
    long countTodayLogins();

    /** 本月新增账号数（本月 1 号 00:00 起算） */
    @Select("SELECT COUNT(1) FROM sso_account WHERE deleted = 0 AND created_at >= DATE_FORMAT(CURDATE(), '%Y-%m-01')")
    long countMonthNewAccounts();

    /**
     * 近 N 天登录趋势：按日期聚合成功/失败次数。
     * <p>
     * 用日期字段范围过滤（走 idx_created），MySQL 端按 DATE(created_at) 分组；
     * 缺勤日期由 Service 层补 0，保证图表横轴连续。
     */
    @Select("""
            SELECT DATE(created_at) AS day,
                   SUM(CASE WHEN result = 'success' THEN 1 ELSE 0 END) AS success,
                   SUM(CASE WHEN result = 'fail' THEN 1 ELSE 0 END) AS fail
            FROM sso_login_log
            WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} - 1 DAY)
            GROUP BY DATE(created_at)
            ORDER BY day
            """)
    List<Map<String, Object>> loginTrend(@Param("days") int days);

    /** 账号状态分布：1正常 2禁用 3锁定 4未激活 */
    @Select("""
            SELECT status AS `key`, COUNT(1) AS cnt
            FROM sso_account WHERE deleted = 0
            GROUP BY status ORDER BY status
            """)
    List<Map<String, Object>> accountStatus();

    /** 登录方式分布（按登录日志实际出现的类型聚合） */
    @Select("""
            SELECT login_type AS `key`, COUNT(1) AS cnt
            FROM sso_login_log
            GROUP BY login_type ORDER BY cnt DESC
            """)
    List<Map<String, Object>> loginTypes();

    /* ---------------- 数据权限范围：先按 scopeCode 反查账号集合，再按同一集合统计 ---------------- */

    /** 数据范围内有效账号的登录名；失败日志 account_id 可能为空，需按登录名补充匹配 */
    @Select("""
            <script>
            SELECT username FROM sso_account
            WHERE deleted = 0
              AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<String> usernamesByIds(@Param("ids") List<Long> ids);

    /** 数据范围内有效账号数 */
    @Select("""
            <script>
            SELECT COUNT(1) FROM sso_account
            WHERE deleted = 0 AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    long countScopedAccounts(@Param("ids") List<Long> ids);

    /** 数据范围内账号被授权过的启用应用数 */
    @Select("""
            <script>
            SELECT COUNT(DISTINCT ap.id)
            FROM sso_application ap
            JOIN sso_account_app aa ON aa.app_id = ap.id
            WHERE ap.status = 1 AND ap.deleted = 0
              AND aa.account_id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    long countScopedApps(@Param("ids") List<Long> ids);

    /** 数据范围内账号持有的有效角色数 */
    @Select("""
            <script>
            SELECT COUNT(DISTINCT r.id)
            FROM sso_role r
            JOIN sso_account_role ar ON ar.role_id = r.id
            WHERE r.deleted = 0
              AND ar.account_id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    long countScopedRoles(@Param("ids") List<Long> ids);

    /** 数据范围内账号通过角色实际持有的有效权限码数 */
    @Select("""
            <script>
            SELECT COUNT(DISTINCT p.id)
            FROM sso_permission p
            JOIN sso_role_permission rp ON rp.permission_id = p.id
            JOIN sso_account_role ar ON ar.role_id = rp.role_id
            WHERE p.deleted = 0
              AND ar.account_id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    long countScopedPermissions(@Param("ids") List<Long> ids);

    /** 数据范围内今日登录次数（成功+失败） */
    @Select("""
            <script>
            SELECT COUNT(1) FROM sso_login_log l
            WHERE l.created_at >= CURDATE()
              <if test="accountIds != null and accountIds.size() > 0">
              AND (
                l.account_id IN
                <foreach collection="accountIds" item="id" open="(" separator="," close=")">#{id}</foreach>
                <if test="usernames != null and usernames.size() > 0">
                OR l.username IN
                <foreach collection="usernames" item="name" open="(" separator="," close=")">#{name}</foreach>
                </if>
              )
              </if>
            </script>
            """)
    long countScopedTodayLogins(@Param("accountIds") List<Long> accountIds,
                                @Param("usernames") List<String> usernames);

    /** 数据范围内本月新增账号数 */
    @Select("""
            <script>
            SELECT COUNT(1) FROM sso_account
            WHERE deleted = 0
              AND created_at >= DATE_FORMAT(CURDATE(), '%Y-%m-01')
              AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    long countScopedMonthNewAccounts(@Param("ids") List<Long> ids);

    /** 数据范围内近 N 天登录趋势 */
    @Select("""
            <script>
            SELECT DATE(l.created_at) AS day,
                   SUM(CASE WHEN l.result = 'success' THEN 1 ELSE 0 END) AS success,
                   SUM(CASE WHEN l.result = 'fail' THEN 1 ELSE 0 END) AS fail
            FROM sso_login_log l
            WHERE l.created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} - 1 DAY)
              <if test="accountIds != null and accountIds.size() > 0">
              AND (
                l.account_id IN
                <foreach collection="accountIds" item="id" open="(" separator="," close=")">#{id}</foreach>
                <if test="usernames != null and usernames.size() > 0">
                OR l.username IN
                <foreach collection="usernames" item="name" open="(" separator="," close=")">#{name}</foreach>
                </if>
              )
              </if>
            GROUP BY DATE(l.created_at)
            ORDER BY day
            </script>
            """)
    List<Map<String, Object>> scopedLoginTrend(@Param("days") int days,
                                               @Param("accountIds") List<Long> accountIds,
                                               @Param("usernames") List<String> usernames);

    /** 数据范围内账号状态分布 */
    @Select("""
            <script>
            SELECT status AS `key`, COUNT(1) AS cnt
            FROM sso_account
            WHERE deleted = 0 AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY status ORDER BY status
            </script>
            """)
    List<Map<String, Object>> scopedAccountStatus(@Param("ids") List<Long> ids);

    /** 数据范围内登录方式分布 */
    @Select("""
            <script>
            SELECT l.login_type AS `key`, COUNT(1) AS cnt
            FROM sso_login_log l
            WHERE 1 = 1
              <if test="accountIds != null and accountIds.size() > 0">
              AND (
                l.account_id IN
                <foreach collection="accountIds" item="id" open="(" separator="," close=")">#{id}</foreach>
                <if test="usernames != null and usernames.size() > 0">
                OR l.username IN
                <foreach collection="usernames" item="name" open="(" separator="," close=")">#{name}</foreach>
                </if>
              )
              </if>
            GROUP BY l.login_type ORDER BY cnt DESC
            </script>
            """)
    List<Map<String, Object>> scopedLoginTypes(@Param("accountIds") List<Long> accountIds,
                                               @Param("usernames") List<String> usernames);
}
