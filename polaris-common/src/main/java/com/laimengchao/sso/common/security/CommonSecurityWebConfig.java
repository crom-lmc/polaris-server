package com.laimengchao.sso.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * 全服务统一的 Web 安全配置（由各服务扫描 common 包生效，只作用于 Servlet 应用）。
 * <ul>
 *   <li>{@code /internal/**}：统一用 {@link InternalTokenInterceptor} 做 fail-closed 准入校验</li>
 *   <li>CORS：默认关闭（前端统一同源代理访问）；需要时通过
 *       {@code polaris.security.cors.allowed-origins} 配置精确域名白名单</li>
 * </ul>
 * 这样新增服务无需再写一遍 CORS 与内部令牌拦截器。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(WebMvcConfigurer.class)
public class CommonSecurityWebConfig implements WebMvcConfigurer {

    private final InternalTokenInterceptor internalTokenInterceptor;
    private final List<String> allowedOrigins;
    private final List<String> downloadPathPatterns;

    public CommonSecurityWebConfig(
            @Value("${polaris.security.internal-token:${SSO_INTERNAL_TOKEN:}}") String internalToken,
            @Value("${polaris.security.cors.allowed-origins:}") String allowedOrigins,
            @Value("${polaris.security.download-path-patterns:}") String downloadPathPatterns) {
        this.internalTokenInterceptor = new InternalTokenInterceptor(internalToken);
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        this.downloadPathPatterns = Arrays.stream(downloadPathPatterns.split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .toList();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalTokenInterceptor)
                .addPathPatterns("/internal/**")
                .order(1);
        if (!downloadPathPatterns.isEmpty()) {
            registry.addInterceptor(new DownloadSecurityInterceptor())
                    .addPathPatterns(downloadPathPatterns.toArray(String[]::new))
                    .order(2);
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 默认不放行任何跨域来源：生产前端与 API 同源（nginx 代理 /api），无需 CORS。
        if (allowedOrigins.isEmpty()) {
            return;
        }
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
