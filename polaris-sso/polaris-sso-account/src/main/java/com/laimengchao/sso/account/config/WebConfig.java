package com.laimengchao.sso.account.config;

import com.laimengchao.sso.account.security.LoginContextInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 拦截器注册：把网关注入的可信头转成 AccountContext（全站）。
 * <p>
 * {@code /internal/**} 准入由 common 的 {@code CommonSecurityWebConfig} 统一注册。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LoginContextInterceptor loginContextInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginContextInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/internal/**", "/error", "/actuator/**", "/favicon.ico")
                .order(0);
    }
}
