package com.laimengchao.sso.settings.module.approval.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 调业务方 / 账号中心的内部接口。
 * <p>
 * <b>为什么用裸 HTTP 而不是 Feign</b>：ehr 已经依赖 settings-client（它要调本服务的字典与主数据），
 * 如果 settings 反过来依赖 ehr 的模块，就成了循环依赖。这些调用点只有两三处、报文极简，
 * 用 RestTemplate 直连更划算，也便于单独设置超时（不能拖垮审批主链路）。
 * <p>
 * 统一要求对端带上 {@code X-Internal-Token}，与 settings-service 自己的 InternalTokenInterceptor 同一套暗号。
 */
@Slf4j
@Component
public class InternalHttpClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String internalToken;

    public InternalHttpClient(ObjectMapper objectMapper,
                              @Value("${approval.internal-token:${settings.internal-token:${SSO_INTERNAL_TOKEN:}}}") String internalToken) {
        this.objectMapper = objectMapper;
        this.internalToken = internalToken;
        // 连接 3s / 读 5s：审批人解析是流程推进的前置步骤，超时必须短，失败按「解析不到人」处理
        org.springframework.http.client.SimpleClientHttpRequestFactory f =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(f);
    }

    /**
     * GET 并取 R 包的 data 节点；任何异常返回 null（调用方按「无人可审」降级）。
     */
    public JsonNode getData(String url) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            if (internalToken != null && !internalToken.isBlank()) {
                headers.set("X-Internal-Token", internalToken);
            }
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            String body = resp.getBody();
            if (body == null || body.isBlank()) {
                return null;
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode code = root.get("code");
            if (code != null && code.asInt(0) != 0) {
                log.warn("内部接口返回业务错误：url={} code={} msg={}", url, code.asInt(),
                        root.path("message").asText());
                return null;
            }
            return root.get("data");
        } catch (Exception ex) {
            log.warn("内部接口调用失败：url={} err={}", url, ex.getMessage());
            return null;
        }
    }

    /**
     * 以 JSON POST，只关心是否成功（2xx）。用于业务回调这类「对端返回什么都不重要」的调用。
     */
    public boolean postOk(String url, String jsonBody) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalToken != null && !internalToken.isBlank()) {
                headers.set("X-Internal-Token", internalToken);
            }
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8), headers), String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            log.warn("回调失败：url={} err={}", url, ex.getMessage());
            return false;
        }
    }

    /**
     * 以表单方式 POST，返回 R 的 data；失败返回 null。
     */
    public JsonNode postJson(String url, Object body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalToken != null && !internalToken.isBlank()) {
                headers.set("X-Internal-Token", internalToken);
            }
            String json = objectMapper.writeValueAsString(body);
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(json, headers), String.class);
            String respBody = resp.getBody();
            if (respBody == null || respBody.isBlank()) {
                return null;
            }
            JsonNode root = objectMapper.readTree(respBody);
            JsonNode code = root.get("code");
            if (code != null && code.asInt(0) != 0) {
                log.warn("内部接口返回业务错误：url={} code={} msg={}", url, code.asInt(),
                        root.path("message").asText());
                return null;
            }
            return root.get("data");
        } catch (Exception ex) {
            log.warn("内部接口调用失败：url={} err={}", url, ex.getMessage());
            return null;
        }
    }
}
