package com.laimengchao.sso.openapi.module.outbound.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.openapi.module.outbound.service.OutboundService;
import com.laimengchao.sso.openapi.security.OpenapiAuthFilter;
import com.laimengchao.sso.openapi.security.dto.CallerIdentity;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 出站接口（内网业务服务调用，X-Internal-Token 准入）。
 * <p>
 * 渠道编码直接体现在路径里，api_code 由渠道映射（email → outbound.email.send）；
 * 新增渠道时在 {@link #apiCodeOf(String)} 补一行即可，其余编排逻辑不动。
 */
@RestController
@RequestMapping("/api/open/outbound")
@RequiredArgsConstructor
public class OutboundController {

    private final OutboundService outboundService;

    @PostMapping("/{channel}/send")
    public R<?> send(@PathVariable String channel,
                     @RequestBody(required = false) Map<String, Object> body,
                     HttpServletRequest req) {
        CallerIdentity identity = (CallerIdentity) req.getAttribute(OpenapiAuthFilter.ATTR_IDENTITY);
        String traceId = req.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        Object data = outboundService.send(new OutboundService.SendContext(
                apiCodeOf(channel), channel, identity, body, clientIp(req), traceId));
        return R.ok(data);
    }

    /** 渠道编码 → 开放 API 目录 api_code。 */
    private String apiCodeOf(String channel) {
        return switch (channel) {
            case "email" -> "outbound.email.send";
            default -> "outbound." + channel + ".send";
        };
    }

    private String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
