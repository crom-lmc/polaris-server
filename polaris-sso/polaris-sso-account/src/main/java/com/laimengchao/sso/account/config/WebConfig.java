package com.laimengchao.sso.account.config;

import com.laimengchao.sso.account.security.InternalTokenInterceptor;
import com.laimengchao.sso.account.security.LoginContextInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 拦截器注册：
 * <ul>
 *   <li>{@link LoginContextInterceptor}：把网关注入的可信头转成 AccountContext（全站）</li>
 *   <li>{@link InternalTokenInterceptor}：/internal/** 服务间准入校验</li>
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LoginContextInterceptor loginContextInterceptor;
    private final InternalTokenInterceptor internalTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginContextInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/internal/**", "/error", "/actuator/**", "/favicon.ico")
                .order(0);

        registry.addInterceptor(internalTokenInterceptor)
                .addPathPatterns("/internal/**")
                .order(1);
    }
}
