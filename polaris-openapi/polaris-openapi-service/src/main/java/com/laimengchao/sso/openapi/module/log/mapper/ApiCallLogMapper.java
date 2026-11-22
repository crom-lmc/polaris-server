package com.laimengchao.sso.openapi.module.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.openapi.module.log.entity.ApiCallLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 调用日志 mapper（只写为主，查询供管理端）。 */
public interface ApiCallLogMapper extends BaseMapper<ApiCallLog> {

    /**
     * 统计聚合（单次 SQL 完成，避免把明细拉到内存）。条件用可空参数 + {@code <if>} 由 XML/注解拼接，
     * 这里用注解 SQL 配合 COALESCE 处理「空参数=不过滤」。
     */
    @Select("<script>" +
            "SELECT COUNT(*) AS total, " +
            "SUM(CASE WHEN http_status &lt; 400 THEN 1 ELSE 0 END) AS success, " +
            "SUM(CASE WHEN http_status &gt;= 400 THEN 1 ELSE 0 END) AS failed, " +
            "COALESCE(AVG(cost_ms),0) AS avg_cost, " +
            "COUNT(DISTINCT app_code) AS app_count " +
            "FROM api_call_log WHERE 1=1 " +
            "<if test='direction != null and direction != \"\"'>" +
            " AND direction = #{direction}</if> " +
            "<if test='appCode != null and appCode != \"\"'>" +
            " AND app_code = #{appCode}</if> " +
            "<if test='apiCode != null and apiCode != \"\"'>" +
            " AND api_code = #{apiCode}</if> " +
            "<if test='from != null and from != \"\"'>" +
            " AND created_at &gt;= #{from}</if> " +
            "<if test='to != null and to != \"\"'>" +
            " AND created_at &lt;= #{to}</if> " +
            "</script>")
    Map<String, Object> selectOverview(@Param("direction") String direction,
                                        @Param("appCode") String appCode,
                                        @Param("apiCode") String apiCode,
                                        @Param("from") String from,
                                        @Param("to") String to);

    /** 失败 TOP API（按 api_code 聚合失败数，倒序取 limit 条）。 */
    @Select("<script>" +
            "SELECT api_code AS apiCode, COUNT(*) AS failed " +
            "FROM api_call_log WHERE http_status &gt;= 400 " +
            "<if test='direction != null and direction != \"\"'>" +
            " AND direction = #{direction}</if> " +
            "<if test='from != null and from != \"\"'>" +
            " AND created_at &gt;= #{from}</if> " +
            "<if test='to != null and to != \"\"'>" +
            " AND created_at &lt;= #{to}</if> " +
            "GROUP BY api_code ORDER BY failed DESC LIMIT #{limit}" +
            "</script>")
    List<Map<String, Object>> selectTopFailed(@Param("direction") String direction,
                                               @Param("from") String from,
                                               @Param("to") String to,
                                               @Param("limit") int limit);

    /** 调用趋势（按天聚合）。date_format 兼容 MySQL 5.7。 */
    @Select("<script>" +
            "SELECT DATE_FORMAT(created_at,'%Y-%m-%d') AS day, COUNT(*) AS total, " +
            "SUM(CASE WHEN http_status &gt;= 400 THEN 1 ELSE 0 END) AS failed " +
            "FROM api_call_log WHERE 1=1 " +
            "<if test='direction != null and direction != \"\"'>" +
            " AND direction = #{direction}</if> " +
            "<if test='appCode != null and appCode != \"\"'>" +
            " AND app_code = #{appCode}</if> " +
            "<if test='from != null and from != \"\"'>" +
            " AND created_at &gt;= #{from}</if> " +
            "<if test='to != null and to != \"\"'>" +
            " AND created_at &lt;= #{to}</if> " +
            "GROUP BY day ORDER BY day" +
            "</script>")
    List<Map<String, Object>> selectTrend(@Param("direction") String direction,
                                           @Param("appCode") String appCode,
                                           @Param("from") String from,
                                           @Param("to") String to);

    /** P95 耗时：用有序 cost_ms 的第 95 百分位（近似，MySQL 5.7 兼容写法）。 */
    @Select("<script>" +
            "SELECT COALESCE(SUBSTRING_INDEX(SUBSTRING_INDEX(GROUP_CONCAT(cost_ms ORDER BY cost_ms), ',', " +
            "GREATEST(1, ROUND(COUNT(*) * 0.95))), ',', -1), 0) AS p95 " +
            "FROM api_call_log WHERE 1=1 " +
            "<if test='direction != null and direction != \"\"'>" +
            " AND direction = #{direction}</if> " +
            "<if test='appCode != null and appCode != \"\"'>" +
            " AND app_code = #{appCode}</if> " +
            "<if test='apiCode != null and apiCode != \"\"'>" +
            " AND api_code = #{apiCode}</if> " +
            "<if test='from != null and from != \"\"'>" +
            " AND created_at &gt;= #{from}</if> " +
            "<if test='to != null and to != \"\"'>" +
            " AND created_at &lt;= #{to}</if> " +
            "</script>")
    Long selectP95(@Param("direction") String direction,
                    @Param("appCode") String appCode,
                    @Param("apiCode") String apiCode,
                    @Param("from") String from,
                    @Param("to") String to);

    /** 最近异常：最近 N 条失败调用（脱敏字段已在落库时处理，这里只取审计展示列）。 */
    @Select("<script>" +
            "SELECT trace_id AS traceId, direction, app_code AS appCode, api_code AS apiCode, " +
            "channel, http_status AS httpStatus, biz_code AS bizCode, cost_ms AS costMs, " +
            "error_msg AS errorMsg, created_at AS createdAt " +
            "FROM api_call_log WHERE http_status &gt;= 400 " +
            "<if test='direction != null and direction != \"\"'>" +
            " AND direction = #{direction}</if> " +
            "<if test='appCode != null and appCode != \"\"'>" +
            " AND app_code = #{appCode}</if> " +
            "<if test='apiCode != null and apiCode != \"\"'>" +
            " AND api_code = #{apiCode}</if> " +
            "<if test='from != null and from != \"\"'>" +
            " AND created_at &gt;= #{from}</if> " +
            "<if test='to != null and to != \"\"'>" +
            " AND created_at &lt;= #{to}</if> " +
            "ORDER BY created_at DESC LIMIT #{limit}" +
            "</script>")
    List<Map<String, Object>> selectRecentErrors(@Param("direction") String direction,
                                                  @Param("appCode") String appCode,
                                                  @Param("apiCode") String apiCode,
                                                  @Param("from") String from,
                                                  @Param("to") String to,
                                                  @Param("limit") int limit);

    /** 按应用排行：调用量 + 成功/失败数，倒序取 limit 条。 */
    @Select("<script>" +
            "SELECT app_code AS appCode, COUNT(*) AS total, " +
            "SUM(CASE WHEN http_status &lt; 400 THEN 1 ELSE 0 END) AS success, " +
            "SUM(CASE WHEN http_status &gt;= 400 THEN 1 ELSE 0 END) AS failed, " +
            "COALESCE(AVG(cost_ms),0) AS avgCost " +
            "FROM api_call_log WHERE 1=1 " +
            "<if test='direction != null and direction != \"\"'>" +
            " AND direction = #{direction}</if> " +
            "<if test='from != null and from != \"\"'>" +
            " AND created_at &gt;= #{from}</if> " +
            "<if test='to != null and to != \"\"'>" +
            " AND created_at &lt;= #{to}</if> " +
            "GROUP BY app_code ORDER BY total DESC LIMIT #{limit}" +
            "</script>")
    List<Map<String, Object>> selectTopApps(@Param("direction") String direction,
                                             @Param("from") String from,
                                             @Param("to") String to,
                                             @Param("limit") int limit);

    /** 按 API 排行：调用量 + 成功/失败 + 平均耗时，倒序取 limit 条。 */
    @Select("<script>" +
            "SELECT api_code AS apiCode, COUNT(*) AS total, " +
            "SUM(CASE WHEN http_status &lt; 400 THEN 1 ELSE 0 END) AS success, " +
            "SUM(CASE WHEN http_status &gt;= 400 THEN 1 ELSE 0 END) AS failed, " +
            "COALESCE(AVG(cost_ms),0) AS avgCost " +
            "FROM api_call_log WHERE 1=1 " +
            "<if test='direction != null and direction != \"\"'>" +
            " AND direction = #{direction}</if> " +
            "<if test='from != null and from != \"\"'>" +
            " AND created_at &gt;= #{from}</if> " +
            "<if test='to != null and to != \"\"'>" +
            " AND created_at &lt;= #{to}</if> " +
            "GROUP BY api_code ORDER BY total DESC LIMIT #{limit}" +
            "</script>")
    List<Map<String, Object>> selectTopApis(@Param("direction") String direction,
                                             @Param("from") String from,
                                             @Param("to") String to,
                                             @Param("limit") int limit);

    /** 耗时分布：按耗时区间分桶计数（&lt;100/100-500/500-1000/1000-3000/&gt;=3000 ms）。 */
    @Select("<script>" +
            "SELECT " +
            "SUM(CASE WHEN cost_ms &lt; 100 THEN 1 ELSE 0 END) AS lt100, " +
            "SUM(CASE WHEN cost_ms &gt;= 100 AND cost_ms &lt; 500 THEN 1 ELSE 0 END) AS b100_500, " +
            "SUM(CASE WHEN cost_ms &gt;= 500 AND cost_ms &lt; 1000 THEN 1 ELSE 0 END) AS b500_1000, " +
            "SUM(CASE WHEN cost_ms &gt;= 1000 AND cost_ms &lt; 3000 THEN 1 ELSE 0 END) AS b1000_3000, " +
            "SUM(CASE WHEN cost_ms &gt;= 3000 THEN 1 ELSE 0 END) AS gte3000 " +
            "FROM api_call_log WHERE 1=1 " +
            "<if test='direction != null and direction != \"\"'>" +
            " AND direction = #{direction}</if> " +
            "<if test='appCode != null and appCode != \"\"'>" +
            " AND app_code = #{appCode}</if> " +
            "<if test='apiCode != null and apiCode != \"\"'>" +
            " AND api_code = #{apiCode}</if> " +
            "<if test='from != null and from != \"\"'>" +
            " AND created_at &gt;= #{from}</if> " +
            "<if test='to != null and to != \"\"'>" +
            " AND created_at &lt;= #{to}</if> " +
            "</script>")
    Map<String, Object> selectCostDistribution(@Param("direction") String direction,
                                                @Param("appCode") String appCode,
                                                @Param("apiCode") String apiCode,
                                                @Param("from") String from,
                                                @Param("to") String to);
}
