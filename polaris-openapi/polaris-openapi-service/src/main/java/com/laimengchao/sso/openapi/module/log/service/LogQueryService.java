package com.laimengchao.sso.openapi.module.log.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.openapi.module.log.entity.ApiCallLog;
import com.laimengchao.sso.openapi.module.log.mapper.ApiCallLogMapper;
import com.laimengchao.sso.openapi.module.log.vo.LogQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 调用日志查询（管理端）：分页 + 条件过滤。报文在写入时已脱敏，这里原样返回，不再二次处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogQueryService {

    private final ApiCallLogMapper mapper;

    /** 分页查询。 */
    public IPage<ApiCallLog> page(LogQuery q) {
        int page = Math.max(1, q.getPage());
        int size = Math.min(200, Math.max(1, q.getSize()));
        Page<ApiCallLog> p = new Page<>(page, size);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ApiCallLog> w = wrapper(q);
        w.orderByDesc(ApiCallLog::getCreatedAt);
        return mapper.selectPage(p, w);
    }

    /** 导出：不分页，最多 5000 条，防止一次性拉爆内存。 */
    public List<ApiCallLog> export(LogQuery q) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ApiCallLog> w = wrapper(q);
        w.last("ORDER BY created_at DESC LIMIT 5000");
        return mapper.selectList(w);
    }

    /** 详情。 */
    public ApiCallLog detail(Long id) {
        return mapper.selectById(id);
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ApiCallLog> wrapper(LogQuery q) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ApiCallLog> w =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (q.getDirection() != null && !q.getDirection().isBlank()) {
            w.eq(ApiCallLog::getDirection, q.getDirection().trim());
        }
        if (q.getAppCode() != null && !q.getAppCode().isBlank()) {
            w.eq(ApiCallLog::getAppCode, q.getAppCode().trim());
        }
        if (q.getApiCode() != null && !q.getApiCode().isBlank()) {
            w.eq(ApiCallLog::getApiCode, q.getApiCode().trim());
        }
        if (q.getChannel() != null && !q.getChannel().isBlank()) {
            w.eq(ApiCallLog::getChannel, q.getChannel().trim());
        }
        if (q.getFrom() != null && !q.getFrom().isBlank()) {
            w.ge(ApiCallLog::getCreatedAt, parse(q.getFrom()));
        }
        if (q.getTo() != null && !q.getTo().isBlank()) {
            w.le(ApiCallLog::getCreatedAt, parse(q.getTo()));
        }
        if (Boolean.TRUE.equals(q.getOnlyFailed())) {
            w.apply("(http_status >= 400 OR (biz_code IS NOT NULL AND biz_code <> '0'))");
        }
        return w;
    }

    private LocalDateTime parse(String s) {
        return LocalDateTime.parse(s.trim().replace(' ', 'T'),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    }
}
