package com.laimengchao.sso.openapi.module.log.service;

import com.laimengchao.sso.openapi.module.log.mapper.ApiCallLogMapper;
import com.laimengchao.sso.openapi.module.log.vo.LogQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 统计聚合服务：单次 SQL 完成，避免把明细拉到内存。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatService {

    private final ApiCallLogMapper mapper;

    /** 总览：调用量/成功/失败/成功率/平均耗时/P95/应用数 + 失败 TOP API + 按天趋势。 */
    public Map<String, Object> overview(LogQuery q) {
        String direction = blankToNull(q.getDirection());
        String app = blankToNull(q.getAppCode());
        String api = blankToNull(q.getApiCode());
        String from = blankToNull(q.getFrom());
        String to = blankToNull(q.getTo());

        Map<String, Object> row = mapper.selectOverview(direction, app, api, from, to);
        long total = num(row.get("total"));
        long success = num(row.get("success"));
        long failed = num(row.get("failed"));
        double rate = total == 0 ? 0 : success * 100.0 / total;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("success", success);
        result.put("failed", failed);
        result.put("successRate", Math.round(rate * 100) / 100.0);
        result.put("avgCostMs", Math.round(numDouble(row.get("avg_cost"))));
        result.put("p95CostMs", num(mapper.selectP95(direction, app, api, from, to)));
        result.put("appCount", num(row.get("app_count")));
        result.put("topFailed", mapper.selectTopFailed(direction, from, to, 10));
        result.put("trend", mapper.selectTrend(direction, app, from, to));
        // 设计 §7.2：最近异常 / 按应用·API 排行 / 耗时分布
        result.put("recentErrors", mapper.selectRecentErrors(direction, app, api, from, to, 10));
        result.put("topApps", mapper.selectTopApps(direction, from, to, 10));
        result.put("topApis", mapper.selectTopApis(direction, from, to, 10));
        result.put("costDistribution", normalizeDistribution(
                mapper.selectCostDistribution(direction, app, api, from, to)));
        return result;
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private long num(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private double numDouble(Object o) {
        return o == null ? 0D : ((Number) o).doubleValue();
    }

    /** 空表时 SUM 返回 null，归一化为 0，避免前端渲染 NaN。 */
    private Map<String, Object> normalizeDistribution(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (row != null) {
            out.putAll(row);
        }
        for (String k : new String[]{"lt100", "b100_500", "b500_1000", "b1000_3000", "gte3000"}) {
            out.putIfAbsent(k, 0L);
        }
        return out;
    }
}
