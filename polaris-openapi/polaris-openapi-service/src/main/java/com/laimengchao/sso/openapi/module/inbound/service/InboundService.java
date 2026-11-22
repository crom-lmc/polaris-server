package com.laimengchao.sso.openapi.module.inbound.service;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.openapi.common.OpenapiErrorCode;
import com.laimengchao.sso.openapi.module.endpoint.entity.ApiOpenEndpoint;
import com.laimengchao.sso.openapi.module.endpoint.mapper.ApiOpenEndpointMapper;
import com.laimengchao.sso.openapi.module.integration.EhrInternalApi;
import com.laimengchao.sso.openapi.module.integration.SettingsInternalApi;
import com.laimengchao.sso.openapi.module.log.entity.ApiCallLog;
import com.laimengchao.sso.openapi.module.log.service.CallLogService;
import com.laimengchao.sso.openapi.module.log.service.MaskUtils;
import com.laimengchao.sso.openapi.security.RateLimiter;
import com.laimengchao.sso.openapi.security.dto.CallerIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 接口进（Inbound）核心编排：目录定位 → 权限校验 → 限流 → 下游转发 → 日志落库。
 * <p>
 * 四类只读接口都由本类统一处理，controller 只负责参数绑定与鉴权入口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboundService {

    private final ApiOpenEndpointMapper endpointMapper;
    private final SettingsInternalApi settingsApi;
    private final EhrInternalApi ehrApi;
    private final RateLimiter rateLimiter;
    private final CallLogService callLogService;

    /** 执行一个开放 API 调用，返回下游 data。 */
    public Object invoke(InvokeContext ctx) {
        ApiOpenEndpoint ep = endpointMapper.selectByCode(ctx.apiCode());
        if (ep == null || ep.getStatus() == null || ep.getStatus() != 1) {
            writeLog(ctx, null, 404, OpenapiErrorCode.API_NOT_FOUND.getCode(), "[]", null,
                    OpenapiErrorCode.API_NOT_FOUND.getMessage());
            throw new BizException(OpenapiErrorCode.API_NOT_FOUND);
        }

        // 权限校验：单一来源在统一登录中心签发的 perms
        if (ep.getRequiredPerm() != null && !ep.getRequiredPerm().isBlank()
                && !ctx.identity().getPermissions().contains(ep.getRequiredPerm())) {
            writeLog(ctx, ep, 403, OpenapiErrorCode.PERM_DENIED.getCode(), "[]", null, null);
            throw new BizException(OpenapiErrorCode.PERM_DENIED);
        }

        // 限流
        try {
            rateLimiter.check(ctx.identity().getAppCode(), ep.getApiCode(),
                    ep.getRateLimitPerMin() == null ? 60 : ep.getRateLimitPerMin());
        } catch (BizException e) {
            writeLog(ctx, ep, 429, OpenapiErrorCode.RATE_LIMITED.getCode(), "[]", null, e.getMessage());
            throw e;
        }

        long start = System.currentTimeMillis();
        Object data;
        try {
            data = dispatch(ep, ctx.pathVars(), ctx.body());
            writeLog(ctx, ep, 200, 0, toJson(ctx.body()), toJson(data), null,
                    (int) (System.currentTimeMillis() - start));
            return data;
        } catch (BizException e) {
            int http = e.getErrorCode() != null && e.getErrorCode().httpStatus() > 0
                    ? e.getErrorCode().httpStatus() : 500;
            writeLog(ctx, ep, http, e.getCode(),
                    toJson(ctx.body()), null, e.getMessage(),
                    (int) (System.currentTimeMillis() - start));
            throw e;
        } catch (Exception e) {
            log.error("开放接口下游调用失败：apiCode={} traceId={}", ep.getApiCode(), ctx.traceId(), e);
            writeLog(ctx, ep, 502, OpenapiErrorCode.CHANNEL_ERROR.getCode(),
                    toJson(ctx.body()), null, e.getMessage(),
                    (int) (System.currentTimeMillis() - start));
            throw new BizException(OpenapiErrorCode.CHANNEL_ERROR);
        }
    }

    /** 按 apiCode 分派到具体下游 Feign 调用。 */
    private Object dispatch(ApiOpenEndpoint ep, Map<String, String> vars, Object body) {
        return switch (ep.getApiCode()) {
            case "inbound.master.categories" -> unwrap(settingsApi.bizCategories());
            case "inbound.master.items" -> unwrap(settingsApi.bizItems(vars.get("type")));
            case "inbound.dict.items" -> unwrap(settingsApi.dictItems(vars.get("typeCode")));
            case "inbound.dict.batch" -> unwrap(settingsApi.dictBatch(asMap(body)));
            case "inbound.employee.active" -> unwrap(ehrApi.activeEmployees());
            case "inbound.org.tree" -> unwrap(ehrApi.orgTree());
            case "inbound.org.employees" -> unwrap(ehrApi.orgEmployees(Long.valueOf(vars.get("id"))));
            default -> throw new BizException(OpenapiErrorCode.API_NOT_FOUND);
        };
    }

    private Object unwrap(R<?> r) {
        if (r == null || r.getCode() != 0) {
            throw new BizException(OpenapiErrorCode.CHANNEL_ERROR,
                    r == null ? "下游无响应" : "下游返回：" + r.getMessage());
        }
        return r.getData();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object body) {
        return body instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MaskUtils.mask(com.laimengchao.sso.utils.JsonUtils.toJson(obj));
        } catch (Exception e) {
            return "[serialize-error]";
        }
    }

    private void writeLog(InvokeContext ctx, ApiOpenEndpoint ep, int httpStatus, int bizCode,
                          String req, String resp, String error) {
        writeLog(ctx, ep, httpStatus, bizCode, req, resp, error, 0);
    }

    private void writeLog(InvokeContext ctx, ApiOpenEndpoint ep, int httpStatus, int bizCode,
                          String req, String resp, String error, int costMs) {
        ApiCallLog entry = new ApiCallLog();
        entry.setTraceId(ctx.traceId());
        entry.setDirection("INBOUND");
        entry.setAppCode(ctx.identity() == null ? "" : ctx.identity().getAppCode());
        entry.setApiCode(ctx.apiCode());
        entry.setHttpMethod(ctx.method());
        entry.setRequestPath(ctx.requestPath());
        entry.setReqMasked(req);
        entry.setRespMasked(resp);
        entry.setHttpStatus(httpStatus);
        entry.setBizCode(String.valueOf(bizCode));
        entry.setCostMs(costMs);
        entry.setClientIp(ctx.clientIp());
        entry.setErrorMsg(error);
        entry.setCreatedAt(LocalDateTime.now());
        callLogService.save(entry);
    }

    /** 调用上下文。 */
    public record InvokeContext(String apiCode, CallerIdentity identity, Map<String, String> pathVars,
                                Object body, String method, String requestPath, String clientIp,
                                String traceId) {
    }
}
