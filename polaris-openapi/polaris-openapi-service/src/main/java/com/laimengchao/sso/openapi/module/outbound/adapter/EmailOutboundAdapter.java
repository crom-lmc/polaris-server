package com.laimengchao.sso.openapi.module.outbound.adapter;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.openapi.common.OpenapiErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * 邮件出站适配器：从渠道配置动态构建 SMTP 客户端（凭证不依赖 spring.mail.* 静态配置）。
 * <p>
 * 未配置 SMTP host（或账号/口令为空）时进入 mock 模式：只记录脱敏后的收件人/主题并返回伪 messageId，
 * 便于本地与联调验证出站链路；生产在渠道凭证里配齐 host/port/user/password 即真实发送。
 */
@Slf4j
@Component
public class EmailOutboundAdapter implements OutboundAdapter {

    @Override
    public String channel() {
        return "email";
    }

    @Override
    public OutboundResult send(OutboundRequest req) {
        Map<String, Object> cfg = req.getConfig() == null ? Map.of() : req.getConfig();
        Map<String, Object> cred = req.getCredential() == null ? Map.of() : req.getCredential();
        Map<String, Object> payload = req.getPayload() == null ? Map.of() : req.getPayload();

        String to = str(payload.get("to"));
        String subject = str(payload.get("subject"));
        String body = str(payload.get("body"));
        String host = str(cfg.get("host"));
        String user = str(cred.get("user"));
        String password = str(cred.get("password"));
        String from = str(cfg.get("from"));
        if (from.isBlank()) {
            from = user;
        }

        if (host.isBlank() || user.isBlank() || password.isBlank()) {
            // mock：不打印口令，收件人已由上层 MaskUtils 处理（这里再脱敏一次防止绕过）
            log.info("[邮件出站-MOCK] traceId={} to={} subject={}（SMTP host/user 未配置，未真实发送）",
                    req.getTraceId(), maskEmail(to), subject);
            return OutboundResult.ok("mock-" + UUID.randomUUID().toString().replace("-", ""),
                    "mock 模式：SMTP 未配置，未真实发送");
        }

        if (to.isBlank()) {
            throw new BizException(OpenapiErrorCode.CONFIG_ERROR, "邮件收件人 to 不能为空");
        }

        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(host);
            sender.setPort(cfg.get("port") == null ? 465 : Integer.parseInt(str(cfg.get("port"))));
            sender.setUsername(user);
            sender.setPassword(password);
            sender.setDefaultEncoding("UTF-8");
            boolean ssl = !"false".equalsIgnoreCase(str(cfg.get("ssl"))) && !"0".equals(str(cfg.get("ssl")));
            Properties props = sender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.ssl.enable", String.valueOf(ssl));
            if (!ssl) {
                props.put("mail.smtp.starttls.enable", "true");
            }
            int timeout = req.getTimeoutMs() > 0 ? req.getTimeoutMs() : 5000;
            props.put("mail.smtp.connectiontimeout", String.valueOf(timeout));
            props.put("mail.smtp.timeout", String.valueOf(timeout));
            props.put("mail.smtp.writetimeout", String.valueOf(timeout));

            SimpleMailMessage msg = new SimpleMailMessage();
            if (!from.isBlank()) {
                msg.setFrom(from);
            }
            msg.setTo(to.split(","));
            msg.setSubject(subject == null ? "" : subject);
            msg.setText(body == null ? "" : body);
            sender.send(msg);

            String messageId = "mail-" + UUID.randomUUID().toString().replace("-", "");
            log.info("[邮件出站] 发送成功 traceId={} to={} messageId={}",
                    req.getTraceId(), maskEmail(to), messageId);
            return OutboundResult.ok(messageId, "邮件已发送");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[邮件出站] 发送失败 traceId={} to={} err={}", req.getTraceId(), maskEmail(to), e.getMessage());
            // 超时单独映射为 TIMEOUT(504)，其余归 SEND_FAILED；两者均可由 OutboundResilience 决定重试与熔断
            if (isTimeout(e)) {
                throw new BizException(OpenapiErrorCode.TIMEOUT, "邮件发送超时：" + e.getMessage());
            }
            throw new BizException(OpenapiErrorCode.SEND_FAILED, "邮件发送失败：" + e.getMessage());
        }
    }

    /**
     * 识别超时：仅认 SocketTimeoutException 类型或明确的 "timed out/超时" 语义。
     * 不能用宽松的 "timeout" 关键字——SMTP 客户端错误消息会回显配置的 timeout 参数，
     * 会把连接拒绝（ConnectException）误判为超时。
     */
    private boolean isTimeout(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof java.net.SocketTimeoutException) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null && (msg.toLowerCase().contains("timed out") || msg.contains("超时"))) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private String maskEmail(String to) {
        if (to == null || !to.contains("@")) {
            return "***";
        }
        int at = to.indexOf('@');
        String name = to.substring(0, at);
        String head = name.length() <= 2 ? name : name.substring(0, 2);
        return head + "***" + to.substring(at);
    }
}
