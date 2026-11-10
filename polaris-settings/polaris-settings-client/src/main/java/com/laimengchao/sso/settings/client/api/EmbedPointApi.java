package com.laimengchao.sso.settings.client.api;

import com.laimengchao.sso.settings.client.config.ApprovalFeignConfig;
import com.laimengchao.sso.settings.client.dto.EmbedPointDTO;
import com.laimengchao.sso.settings.client.dto.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * 埋点配置内部接口：其他业务服务按 key 读取配置中心维护的 JSON 值。
 * <p>
 * 走 {@code /internal/embed-points/**}（不经网关），凭 X-Internal-Token 准入。
 * 不存在时 data 为 null，调用方需自行兜底。
 */
@FeignClient(
        name = "settings",
        contextId = "settings-embed-point",
        url = "${settings.client.base-url:http://127.0.0.1:9007}",
        configuration = ApprovalFeignConfig.class)
public interface EmbedPointApi {

    @GetMapping("/internal/embed-points/{code}")
    R<EmbedPointDTO> get(@PathVariable("code") String code);

    @GetMapping("/internal/embed-points")
    R<List<EmbedPointDTO>> list();
}
