package com.laimengchao.sso.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 应用配置快照（sso-account 的 /internal/apps、/{accountId}/apps、/internal/apps/verify 出参）。
 * 雪花 ID 以 Long 承载，Jackson 字符串数字自动适配。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppInfo {

    private Long id;
    private String code;
    private String name;
    private String baseUrl;
    private String redirectUri;
    private String icon;
}
