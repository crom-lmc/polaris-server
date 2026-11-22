package com.laimengchao.sso.openapi.module.outbound.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.openapi.common.OpenapiErrorCode;
import com.laimengchao.sso.openapi.module.channel.entity.ApiOutboundChannel;
import com.laimengchao.sso.openapi.module.channel.service.ChannelService;
import com.laimengchao.sso.openapi.module.endpoint.entity.ApiOpenEndpoint;
import com.laimengchao.sso.openapi.module.endpoint.mapper.ApiOpenEndpointMapper;
import com.laimengchao.sso.openapi.module.log.entity.ApiCallLog;
import com.laimengchao.sso.openapi.module.log.service.CallLogService;
import com.laimengchao.sso.openapi.module.log.service.MaskUtils;
import com.laimengchao.sso.openapi.module.outbound.adapter.OutboundAdapter;
import com.laimengchao.sso.openapi.module.outbound.adapter.OutboundAdapterRegistry;
import com.laimengchao.sso.openapi.module.outbound.adapter.OutboundRequest;
import com.laimengchao.sso.openapi.module.outbound.adapter.OutboundResult;
import com.laimengchao.sso.openapi.module.outbound.resilience.OutboundResilience;
import com.laimengchao.sso.openapi.security.RateLimiter;
import com.laimengchao.sso.openapi.security.dto.CallerIdentity;
import com.laimengchao.sso.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 接口出（Outbound）核心编排：目录定位 → 权限/白名单 → 限流 → 凭证解密 → 适配器调用（重试+熔断）→ 日志落库。
 * <p>
 * 与 Inbound 对称，但方向相反：调用方是内网业务服务（X-Internal-Token 准入），
 * 目标是外部第三方；凭证来自渠道表的 AES-GCM 密文，明文只在内存里传递。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboundService {

    private final ApiOpenEndpointMapper endpointMapper;
    private final ChannelService channelService;
    private final OutboundAdapterRegistry adapterRegistry;
    private final OutboundResilience resilience;
    private final RateLimiter rateLimiter;
    private final CallLogService callLogService;

    /** 出站统一入口。 */
    public Object send(SendContext ctx) {
        // 1) 定位开放 API 目录（按 api_code，如 outbound.email.send）
        ApiOpenEndpoint ep = endpointMapper.selectByCode(ctx.apiCode());
        if (ep == null || ep.getStatus() == null || ep.getStatus() != 1 || ep.getDirection() != 2) {
            throw new BizException(OpenapiErrorCode.API_NOT_FOUND);
        }

        // 2) 权限校验：required_perm ∈ identity.perms（内部身份含 "*" 通配）
        checkPermission(ep, ctx.identity());

        // 3) 渠道配置 + 白名单
        ApiOutboundChannel channel = channelService.requireEnabled(ctx.channel());
        checkAllowedApps(channel, ctx.identity().getAppCode());

        // 4) 限流（按 app + api）
        rateLimiter.check(ctx.identity().getAppCode(), ep.getApiCode(),
                ep.getRateLimitPerMin() == null ? 60 : ep.getRateLimitPerMin());

        long start = System.currentTimeMillis();
        try {
            // 5) 解析业务报文 / 凭证 / 配置
            Map<String, Object> payload = ctx.payload() == null ? Map.of() : ctx.payload();
            Map<String, Object> credential = channelService.resolveCredential(ctx.channel());
            Map<String, Object> config = parseConfig(channel.getAuthConfig());

            OutboundRequest req = OutboundRequest.builder()
                    .channel(ctx.channel())
                    .payload(payload)
                    .credential(credential)
                    .config(config)
                    .timeoutMs(channel.getTimeoutMs() == null ? 5000 : channel.getTimeoutMs())
                    .traceId(ctx.traceId())
                    .build();

            OutboundAdapter adapter = adapterRegistry.get(ctx.channel());
            // 幂等约束（设计 §10）：仅 endpoint 标记 idempotent=1 时才允许自动重试；
            // 非幂等接口（如发邮件，重试会重复投递）强制不重试，避免重复副作用。
            int retryMax = channel.getRetryMax() == null ? 0 : channel.getRetryMax();
            if (ep.getIdempotent() == null || ep.getIdempotent() != 1) {
                if (retryMax > 0) {
                    log.info("出站接口非幂等，禁用自动重试：apiCode={} channel={} 配置retryMax={}",
                            ep.getApiCode(), ctx.channel(), retryMax);
                }
                retryMax = 0;
            }
            OutboundResult result = resilience.execute(ctx.channel(), retryMax, () -> adapter.send(req));

            int cost = (int) (System.currentTimeMillis() - start);
            if (result.isSuccess()) {
                writeLog(ctx, 200, 0, toJson(payload), toJson(safeResult(result)), null, cost);
                return Map.of("messageId", result.getMessageId() == null ? "" : result.getMessageId(),
                        "detail", result.getDetail() == null ? "" : result.getDetail());
            }
            // 不在这里写日志，统一由下方 catch 写，避免与异常路径重复落库
            throw new BizException(OpenapiErrorCode.SEND_FAILED, result.getDetail());
        } catch (BizException e) {
            int http = e.getErrorCode() != null && e.getErrorCode().httpStatus() > 0
                    ? e.getErrorCode().httpStatus() : 500;
            writeLog(ctx, http, e.getCode(), toJson(ctx.payload()), null, e.getMessage(),
                    (int) (System.currentTimeMillis() - start));
            throw e;
        } catch (Exception e) {
            log.error("出站调用异常：channel={} traceId={}", ctx.channel(), ctx.traceId(), e);
            writeLog(ctx, 502, OpenapiErrorCode.CHANNEL_ERROR.getCode(), toJson(ctx.payload()), null,
                    e.getMessage(), (int) (System.currentTimeMillis() - start));
            throw new BizException(OpenapiErrorCode.CHANNEL_ERROR);
        }
    }

    private void checkPermission(ApiOpenEndpoint ep, CallerIdentity identity) {
        String perm = ep.getRequiredPerm();
        if (perm == null || perm.isBlank() || identity.getPermissions().contains("*")) {
            return;
        }
        if (!identity.getPermissions().contains(perm)) {
            throw new BizException(OpenapiErrorCode.PERM_DENIED);
        }
    }

    private void checkAllowedApps(ApiOutboundChannel channel, String appCode) {
        String allowed = channel.getAllowedApps();
        if (allowed == null || allowed.isBlank()) {
            return;
        }
        Set<String> apps = new HashSet<>(Arrays.asList(allowed.split(",")));
        if (!apps.contains(appCode)) {
            throw new BizException(OpenapiErrorCode.APP_NOT_ALLOWED);
        }
    }

    private Map<String, Object> parseConfig(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> map = JsonUtils.fromJson(json, new TypeReference<Map<String, Object>>() {
            });
            return map == null ? Map.of() : map;
        } catch (Exception e) {
            log.warn("渠道 auth_config 解析失败，按空配置处理：{}", e.getMessage());
            return Map.of();
        }
    }

    /** 响应里只保留安全字段，杜绝凭证误入日志。 */
    private OutboundResult safeResult(OutboundResult r) {
        return OutboundResult.ok(r.getMessageId(), r.getDetail());
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MaskUtils.mask(JsonUtils.toJson(obj));
        } catch (Exception e) {
            return "[serialize-error]";
        }
    }

    private void writeLog(SendContext ctx, int httpStatus, int bizCode,
                          String req, String resp, String error, int costMs) {
        ApiCallLog entry = new ApiCallLog();
        entry.setTraceId(ctx.traceId());
        entry.setDirection("OUTBOUND");
        entry.setAppCode(ctx.identity() == null ? "" : ctx.identity().getAppCode());
        entry.setApiCode(ctx.apiCode());
        entry.setChannel(ctx.channel());
        entry.setHttpMethod("POST");
        entry.setRequestPath("/api/open/outbound/" + ctx.channel());
        entry.setReqMasked(req);
        entry.setRespMasked(resp);
        entry.setHttpStatus(httpStatus);
        entry.setBizCode(String.valueOf(bizCode));
        entry.setCostMs(costMs);
        entry.setClientIp(ctx.clientIp());
        entry.setErrorMsg(error == null ? null : MaskUtils.mask(error));
        entry.setCreatedAt(LocalDateTime.now());
        callLogService.save(entry);
    }

    /** 出站调用上下文。 */
    public record SendContext(String apiCode, String channel, CallerIdentity identity,
                              Map<String, Object> payload, String clientIp, String traceId) {
    }
}
