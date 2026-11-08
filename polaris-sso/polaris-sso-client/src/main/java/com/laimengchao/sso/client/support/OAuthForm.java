package com.laimengchao.sso.client.support;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

/**
 * OAuth2 token 请求体的表单编码器。
 * <p>
 * Feign 对 {@code MultiValueMap} 的表单编码依赖 FormHttpMessageConverter，实测请求体
 * 到不了服务端（auth 报「client_id 必填」）。这里手工编码成
 * {@code application/x-www-form-urlencoded} 字符串，保证参数一定送达。
 */
public final class OAuthForm {

    private OAuthForm() {
    }

    public static String encode(Map<String, String> fields) {
        StringJoiner joiner = new StringJoiner("&");
        fields.forEach((name, value) -> {
            if (name != null && value != null) {
                joiner.add(URLEncoder.encode(name, StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        });
        return joiner.toString();
    }
}
