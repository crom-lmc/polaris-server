package com.laimengchao.sso.auth.service;

import com.laimengchao.sso.auth.config.AuthProperties;
import org.springframework.beans.factory.ObjectProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 邮件发送：SMTP 已配置则真实发送，否则打印到控制台（本地调试）。
 * 依赖 spring-boot-starter-mail，但本类不强制其存在——未引入时走控制台。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSender {

    private final AuthProperties props;
    private final org.springframework.beans.factory.ObjectProvider<JavaMailSender> mailSenderProvider;

    public void send(String to, String subject, String text) {
        JavaMailSender mail = mailSenderProvider.getIfAvailable();
        if (mail != null && props.isEmailEnabled()) {
            try {
                SimpleMailMessage m = new SimpleMailMessage();
                m.setFrom(props.getMailFrom());
                m.setTo(to);
                m.setSubject(subject);
                m.setText(text);
                mail.send(m);
                return;
            } catch (Exception e) {
                log.warn("SMTP 发送失败，回退控制台打印: {}", e.getMessage());
            }
        }
        log.info("[邮箱验证码][控制台/未配置SMTP] to={} subject={} body={}", to, subject, text);
    }
}
