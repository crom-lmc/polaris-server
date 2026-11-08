package com.laimengchao.sso.settings.client.api;

import com.laimengchao.sso.settings.client.config.SettingsFeignConfig;
import com.laimengchao.sso.settings.client.dto.DictBatchRequest;
import com.laimengchao.sso.settings.client.dto.DictItemDTO;
import com.laimengchao.sso.settings.client.dto.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * 字典（cfg_dict_*）跨服查询 Feign 接口——settings 对外提供的数据面之一。
 * <p>
 * 覆盖全部字典类型（general.id_type / general.nation / ehr.gender / ehr.marital_status 等）：
 * 单类型 {@link #dictItems} 或批量 {@link #dictBatch}。
 * 走 {@code /internal/dicts/**}（不经网关），凭 X-Internal-Token 准入（见 SettingsFeignConfig）。
 * 直连 url 模式（不经注册中心负载均衡）：本套服务本地/生产都用固定地址，
 * {@code name} 仅作 Feign 的客户端标识。
 * <p>
 * 一般不直接注入本接口，注入 {@code com.laimengchao.sso.settings.client.support.SettingsClient}
 * （带 60s 缓存与故障兜底）。
 */
@FeignClient(
        name = "settings",
        contextId = "settings-dict",
        url = "${settings.client.base-url:http://127.0.0.1:9007}",
        configuration = SettingsFeignConfig.class)
public interface DictApi {

    /** 字典项列表（启用项，保持配置中心排序），类型不存在/停用返回空列表 */
    @GetMapping("/internal/dicts/{typeCode}")
    R<List<DictItemDTO>> dictItems(@PathVariable("typeCode") String typeCode);

    /** 字典批量拉取（查询）：一次请求拿多个类型，返回 typeCode → items */
    @PostMapping("/internal/dicts/batch")
    R<Map<String, List<DictItemDTO>>> dictBatch(@RequestBody DictBatchRequest request);
}
