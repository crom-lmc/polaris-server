package com.laimengchao.sso.openapi.security;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.openapi.common.OpenapiErrorCode;
import com.laimengchao.sso.openapi.module.setting.service.SettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 第三方来源 IP 白名单校验（P2）。
 * <p>
 * 配置来自平台设置 {@code ip.whitelist}，支持逗号/换行分隔，条目可为：
 * <ul>
 *   <li>精确 IPv4，如 {@code 192.168.1.10}；</li>
 *   <li>CIDR 段，如 {@code 10.0.0.0/8}；</li>
 *   <li>{@code *} 或空配置：不限制（保持 P0/P1 行为，默认不误伤）。</li>
 * </ul>
 * 只校验第三方通道；管理端（用户令牌）与出站（内部服务）不受此限。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IpWhitelist {

    private final SettingService settingService;

    /** 校验来源 IP，不在白名单抛 403。 */
    public void check(String clientIp) {
        String raw = settingService.get(SettingService.KEY_IP_WHITELIST, "");
        if (raw == null || raw.isBlank()) {
            return; // 未配置=不限制
        }
        String[] rules = raw.split("[,\\r\\n]+");
        for (String rule : rules) {
            String r = rule.trim();
            if (r.isEmpty() || "*".equals(r)) {
                return;
            }
            if (matches(clientIp, r)) {
                return;
            }
        }
        log.warn("第三方来源 IP 不在白名单：ip={}", clientIp);
        throw new BizException(OpenapiErrorCode.IP_NOT_ALLOWED);
    }

    private boolean matches(String ip, String rule) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        if (rule.equals(ip)) {
            return true;
        }
        int slash = rule.indexOf('/');
        if (slash > 0) {
            try {
                String network = rule.substring(0, slash);
                int prefix = Integer.parseInt(rule.substring(slash + 1).trim());
                return inCidr(ip, network, prefix);
            } catch (Exception e) {
                log.warn("非法的白名单 CIDR 规则：{}", rule);
                return false;
            }
        }
        return false;
    }

    /** IPv4 CIDR 匹配（按 32 位无符号比较，避免 Java int 符号问题）。 */
    private boolean inCidr(String ip, String network, int prefix) {
        Long ipBits = ipv4ToLong(ip);
        Long netBits = ipv4ToLong(network);
        if (ipBits == null || netBits == null || prefix < 0 || prefix > 32) {
            return false;
        }
        long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        return (ipBits & mask) == (netBits & mask);
    }

    private Long ipv4ToLong(String ip) {
        String[] parts = ip.trim().split("\\.");
        if (parts.length != 4) {
            return null;
        }
        long result = 0;
        for (String part : parts) {
            int v;
            try {
                v = Integer.parseInt(part.trim());
            } catch (NumberFormatException e) {
                return null;
            }
            if (v < 0 || v > 255) {
                return null;
            }
            result = (result << 8) | v;
        }
        return result;
    }
}
