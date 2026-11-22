package com.laimengchao.sso.openapi.module.log.service;

import com.laimengchao.sso.openapi.module.log.entity.ApiCallLog;
import com.laimengchao.sso.openapi.module.log.mapper.ApiCallLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 调用日志服务：异步落库，报文在进入本层前已脱敏。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallLogService {

    private final ApiCallLogMapper callLogMapper;

    @Async("logExecutor")
    public void save(ApiCallLog logEntry) {
        try {
            if (logEntry.getCreatedAt() == null) {
                logEntry.setCreatedAt(LocalDateTime.now());
            }
            callLogMapper.insert(logEntry);
        } catch (Exception e) {
            // 日志失败绝不影响业务链路，但要留痕可排查
            log.error("调用日志落库失败：traceId={} apiCode={}", logEntry.getTraceId(),
                    logEntry.getApiCode(), e);
        }
    }
}
