package com.laimengchao.sso.openapi.module.outbound.adapter;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.openapi.common.OpenapiErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 出站适配器注册表：按渠道编码路由到对应适配器。 */
@Component
@RequiredArgsConstructor
public class OutboundAdapterRegistry {

    private final List<OutboundAdapter> adapters;

    public OutboundAdapter get(String channel) {
        Map<String, OutboundAdapter> map = adapters.stream()
                .collect(Collectors.toMap(OutboundAdapter::channel, Function.identity(), (a, b) -> a));
        OutboundAdapter adapter = map.get(channel);
        if (adapter == null) {
            throw new BizException(OpenapiErrorCode.CHANNEL_NOT_FOUND, "未找到渠道适配器：" + channel);
        }
        return adapter;
    }
}
