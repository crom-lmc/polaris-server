package com.laimengchao.sso.settings.module.notice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.laimengchao.sso.settings.module.approval.service.InternalHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 邮件通道：收件邮箱从统一账号中心内部接口取。
 * <p>
 * SMTP 未配置（spring.mail.host 为空）或取不到邮箱时只记日志、不抛异常——
 * 站内消息已经落库，邮件是附加通道，不能因为它失败把业务主链路一起带回滚。
 */
@Slf4j
@Component
public class NoticeMailSender {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final InternalHttpClient httpClient;
    private final String accountBaseUrl;
    private final String smtpHost;
    private final String from;

    public NoticeMailSender(ObjectProvider<JavaMailSender> mailSenderProvider,
                            InternalHttpClient httpClient,
                            @Value("${approval.account-base-url:http://127.0.0.1:9002}") String accountBaseUrl,
                            @Value("${spring.mail.host:}") String smtpHost,
                            @Value("${spring.mail.username:}") String from) {
        this.mailSenderProvider = mailSenderProvider;
        this.httpClient = httpClient;
        this.accountBaseUrl = accountBaseUrl;
        this.smtpHost = smtpHost;
        this.from = from;
    }

    /** 给某个账号发一封纯文本邮件；失败只记日志，调用方按「邮件未送达」处理即可 */
    public void send(Long accountId, String subject, String content) {
        if (smtpHost == null || smtpHost.isBlank()) {
            log.info("未配置 SMTP，跳过邮件通知：accountId={} subject={}", accountId, subject);
            return;
        }
        String email = resolveEmail(accountId);
        if (email == null) {
            log.warn("账号无邮箱，跳过邮件通知：accountId={} subject={}", accountId, subject);
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("JavaMailSender 未装配，跳过邮件通知：accountId={}", accountId);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            if (from != null && !from.isBlank()) {
                msg.setFrom(from);
            }
            msg.setTo(email);
            msg.setSubject(subject);
            msg.setText(content);
            sender.send(msg);
        } catch (Exception ex) {
            log.warn("邮件发送失败：accountId={} email={} err={}", accountId, email, ex.getMessage());
        }
    }

    private String resolveEmail(Long accountId) {
        JsonNode data = httpClient.getData(accountBaseUrl + "/internal/accounts/" + accountId + "/contact");
        if (data == null) {
            return null;
        }
        String email = data.path("email").asText("");
        return email.isBlank() ? null : email;
    }
}
