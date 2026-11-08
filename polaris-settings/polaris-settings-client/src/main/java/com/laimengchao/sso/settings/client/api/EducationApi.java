package com.laimengchao.sso.settings.client.api;

import com.laimengchao.sso.settings.client.config.SettingsFeignConfig;
import com.laimengchao.sso.settings.client.dto.BizItemDTO;
import com.laimengchao.sso.settings.client.dto.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * 学历主数据（biz_education）跨服查询 Feign 接口——settings 对外提供的数据面之一。
 * <p>
 * 走 {@code /internal/biz/education}（不经网关），凭 X-Internal-Token 准入（见 SettingsFeignConfig）；
 * 只提供查询，维护走 settings-service 管理端。
 * 一般不直接注入，注入 {@code com.laimengchao.sso.settings.client.support.SettingsClient}。
 */
@FeignClient(
        name = "settings",
        contextId = "settings-education",
        url = "${settings.client.base-url:http://127.0.0.1:9007}",
        configuration = SettingsFeignConfig.class)
public interface EducationApi {

    /** 学历列表（启用项，保持排序） */
    @GetMapping("/internal/biz/education")
    R<List<BizItemDTO>> list();
}
