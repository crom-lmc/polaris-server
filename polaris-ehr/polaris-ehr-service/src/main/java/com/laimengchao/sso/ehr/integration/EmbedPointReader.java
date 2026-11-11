package com.laimengchao.sso.ehr.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.settings.client.dto.EmbedPointDTO;
import com.laimengchao.sso.settings.client.support.SettingsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 埋点配置读取器：业务开关/规则统一从配置中心埋点读取（60s 本地缓存）。
 * <p>
 * 埋点不存在 / 配置中心不可用 / JSON 非法时返回调用方给出的内置默认值，
 * 配置中心故障不放大成业务故障。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbedPointReader {

    private final SettingsClient settingsClient;
    private final ObjectMapper objectMapper;

    /** 读取埋点 JSON 根节点；读不到或非法返回 null */
    public JsonNode root(String code) {
        EmbedPointDTO point = settingsClient.embedPoint(code);
        if (point == null || point.getValue() == null || point.getValue().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(point.getValue());
        } catch (Exception e) {
            log.warn("埋点 {} 解析失败，使用内置默认值：{}", code, e.getMessage());
            return null;
        }
    }

    /** 字符串字段；缺失 / 空白返回 fallback */
    public String text(String code, String field, String fallback) {
        JsonNode root = root(code);
        String value = root == null ? "" : root.path(field).asText("").trim();
        return value.isEmpty() ? fallback : value;
    }

    /** 布尔字段；字段缺失返回 fallback */
    public boolean flag(String code, String field, boolean fallback) {
        JsonNode root = root(code);
        if (root == null || !root.has(field)) {
            return fallback;
        }
        return root.path(field).asBoolean(fallback);
    }

    /** 字符串数组；缺失 / 空数组返回 fallback */
    public List<String> texts(String code, String field, List<String> fallback) {
        JsonNode root = root(code);
        JsonNode node = root == null ? null : root.get(field);
        if (node == null || !node.isArray()) {
            return fallback;
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isEmpty()) {
                out.add(value);
            }
        }
        return out.isEmpty() ? fallback : out;
    }

    /** 整数数组；缺失 / 空数组返回 fallback */
    public List<Integer> numbers(String code, String field, List<Integer> fallback) {
        JsonNode root = root(code);
        JsonNode node = root == null ? null : root.get(field);
        if (node == null || !node.isArray()) {
            return fallback;
        }
        List<Integer> out = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.canConvertToInt()) {
                out.add(item.asInt());
            }
        }
        return out.isEmpty() ? fallback : out;
    }

    /** 长整型数组（兼容数字与字符串两种写法，雪花 ID 超出 int 范围）；缺失 / 空数组返回 fallback */
    public List<Long> longs(String code, String field, List<Long> fallback) {
        JsonNode root = root(code);
        JsonNode node = root == null ? null : root.get(field);
        if (node == null || !node.isArray()) {
            return fallback;
        }
        List<Long> out = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isNumber()) {
                out.add(item.asLong());
                continue;
            }
            try {
                out.add(Long.parseLong(item.asText("").trim()));
            } catch (NumberFormatException ignored) {
                // 非法 ID 直接跳过，不因单个脏值拖垮整个配置
            }
        }
        return out.isEmpty() ? fallback : out;
    }
}
