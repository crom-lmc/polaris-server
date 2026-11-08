package com.laimengchao.sso.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.auth.config.AuthProperties;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 微信开放平台 OAuth：用回调 code 换取 openid。
 * <p>仅在 {@code sso.auth.wechat-appid} 已配置时由 auth 调用；未配置则端点直接返回 501。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WechatService {

    private final RestTemplate plainRestTemplate;
    private final AuthProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String exchangeOpenid(String code) {
        String url = "https://api.weixin.qq.com/sns/oauth2/access_token"
                + "?appid=" + props.getWechatAppid()
                + "&secret=" + props.getWechatSecret()
                + "&code=" + code
                + "&grant_type=authorization_code";
        try {
            String body = plainRestTemplate.getForObject(url, String.class);
            JsonNode node = objectMapper.readTree(body);
            if (node.has("errcode") && node.get("errcode").asInt() != 0) {
                throw new BizException(ErrorCode.BIZ_ERROR,
                        "微信换取 openid 失败: " + node.path("errmsg").asText());
            }
            String openid = node.path("openid").asText();
            if (openid.isBlank()) {
                throw new BizException(ErrorCode.BIZ_ERROR, "微信未返回 openid");
            }
            return openid;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("微信接口调用异常", e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "微信接口调用异常");
        }
    }
}
