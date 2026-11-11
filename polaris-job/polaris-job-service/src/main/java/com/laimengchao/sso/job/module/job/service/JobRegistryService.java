package com.laimengchao.sso.job.module.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.job.client.dto.HandlerDefinition;
import com.laimengchao.sso.job.client.dto.JobDefinitionsRequest;
import com.laimengchao.sso.job.client.dto.JobHeartbeatRequest;
import com.laimengchao.sso.job.module.job.entity.JobRegistry;
import com.laimengchao.sso.job.module.job.mapper.JobRegistryMapper;
import com.laimengchao.sso.job.module.job.vo.ExecutorVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 执行器注册与心跳。在线判定以 last_heartbeat 为准（90 秒窗口），
 * 不信任落库的 online 字段——应用崩溃时没人能来把它改成离线。
 */
@Slf4j
@Service
public class JobRegistryService {

    /** 心跳窗口：超过 90 秒未上报视为离线 */
    public static final int ONLINE_WINDOW_SECONDS = 90;

    private final JobRegistryMapper registryMapper;
    private final ObjectMapper objectMapper;

    public JobRegistryService(JobRegistryMapper registryMapper, ObjectMapper objectMapper) {
        this.registryMapper = registryMapper;
        this.objectMapper = objectMapper;
    }

    /** 启动上报：覆盖 Handler 清单并刷新心跳 */
    public void reportDefinitions(JobDefinitionsRequest request) {
        String appCode = requireText(request == null ? null : request.getAppCode(), "appCode");
        String address = requireText(request.getAddress(), "address");
        List<HandlerDefinition> handlers = request.getHandlers() == null ? List.of() : request.getHandlers();
        String handlersJson = writeHandlers(handlers);

        JobRegistry row = find(appCode, address);
        LocalDateTime now = LocalDateTime.now();
        if (row == null) {
            row = new JobRegistry();
            row.setAppCode(appCode);
            row.setAddress(address);
            row.setHandlers(handlersJson);
            row.setVersion(nvl(request.getVersion()));
            row.setJobCount(handlers.size());
            row.setOnline(1);
            row.setLastHeartbeat(now);
            registryMapper.insert(row);
            log.info("执行器注册：app={} address={} handlers={}", appCode, address, handlers.size());
            return;
        }
        row.setHandlers(handlersJson);
        row.setVersion(nvl(request.getVersion()));
        row.setJobCount(handlers.size());
        row.setOnline(1);
        row.setLastHeartbeat(now);
        registryMapper.updateById(row);
    }

    /** 心跳：只刷新时间；行不存在时补一条空清单，避免早期心跳丢失 */
    public void ping(JobHeartbeatRequest request) {
        String appCode = requireText(request == null ? null : request.getAppCode(), "appCode");
        String address = requireText(request.getAddress(), "address");

        JobRegistry row = find(appCode, address);
        LocalDateTime now = LocalDateTime.now();
        if (row == null) {
            row = new JobRegistry();
            row.setAppCode(appCode);
            row.setAddress(address);
            row.setHandlers("[]");
            row.setVersion("");
            row.setJobCount(0);
            row.setOnline(1);
            row.setLastHeartbeat(now);
            registryMapper.insert(row);
            return;
        }
        row.setOnline(1);
        row.setLastHeartbeat(now);
        registryMapper.updateById(row);
    }

    /** 优雅下线：保留清单，仅标记离线 */
    public void offline(JobHeartbeatRequest request) {
        String appCode = requireText(request == null ? null : request.getAppCode(), "appCode");
        String address = requireText(request.getAddress(), "address");
        JobRegistry row = find(appCode, address);
        if (row == null) {
            return;
        }
        row.setOnline(0);
        registryMapper.updateById(row);
    }

    /** 管理端列表：online 按心跳实时计算 */
    public List<ExecutorVO> list(String appCode, Boolean online) {
        LambdaQueryWrapper<JobRegistry> qw = new LambdaQueryWrapper<>();
        if (appCode != null && !appCode.isBlank()) {
            qw.eq(JobRegistry::getAppCode, appCode.trim());
        }
        qw.orderByDesc(JobRegistry::getLastHeartbeat);
        LocalDateTime now = LocalDateTime.now();
        List<ExecutorVO> result = new ArrayList<>();
        for (JobRegistry row : registryMapper.selectList(qw)) {
            boolean actualOnline = isOnline(row, now);
            if (online != null && online != actualOnline) {
                continue;
            }
            result.add(toVO(row, actualOnline));
        }
        return result;
    }

    /** 某应用全部实例上报的 Handler 并集 */
    public Set<String> knownHandlers(String appCode) {
        if (appCode == null || appCode.isBlank()) {
            return Set.of();
        }
        LambdaQueryWrapper<JobRegistry> qw = new LambdaQueryWrapper<>();
        qw.eq(JobRegistry::getAppCode, appCode.trim());
        Set<String> names = new HashSet<>();
        for (JobRegistry row : registryMapper.selectList(qw)) {
            for (HandlerDefinition definition : readHandlers(row.getHandlers())) {
                if (definition.getName() != null && !definition.getName().isBlank()) {
                    names.add(definition.getName().trim());
                }
            }
        }
        return names;
    }

    private boolean isOnline(JobRegistry row, LocalDateTime now) {
        return row.getLastHeartbeat() != null
                && !row.getLastHeartbeat().isBefore(now.minusSeconds(ONLINE_WINDOW_SECONDS));
    }

    private JobRegistry find(String appCode, String address) {
        LambdaQueryWrapper<JobRegistry> qw = new LambdaQueryWrapper<>();
        qw.eq(JobRegistry::getAppCode, appCode)
                .eq(JobRegistry::getAddress, address)
                .last("limit 1");
        return registryMapper.selectOne(qw);
    }

    private ExecutorVO toVO(JobRegistry row, boolean online) {
        ExecutorVO vo = new ExecutorVO();
        vo.setId(row.getId());
        vo.setAppCode(row.getAppCode());
        vo.setAddress(row.getAddress());
        vo.setHandlers(readHandlers(row.getHandlers()));
        vo.setVersion(row.getVersion());
        vo.setJobCount(row.getJobCount());
        vo.setOnline(online);
        vo.setLastHeartbeat(row.getLastHeartbeat());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setUpdatedAt(row.getUpdatedAt());
        return vo;
    }

    private String writeHandlers(List<HandlerDefinition> handlers) {
        try {
            return objectMapper.writeValueAsString(handlers);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "Handler 清单序列化失败：" + e.getMessage());
        }
    }

    private List<HandlerDefinition> readHandlers(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<HandlerDefinition> handlers =
                    objectMapper.readValue(json, new TypeReference<List<HandlerDefinition>>() {
                    });
            return handlers == null ? List.of() : handlers;
        } catch (Exception e) {
            log.warn("Handler 清单解析失败，按空处理：{}", e.getMessage());
            return List.of();
        }
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, field + " 不能为空");
        }
        return value.trim();
    }

    private String nvl(String value) {
        return value == null ? "" : value.trim();
    }
}
