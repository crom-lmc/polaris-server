package com.laimengchao.sso.blog.config;

import com.laimengchao.sso.blog.common.ratelimit.RateLimitInterceptor;
import com.laimengchao.sso.blog.security.InternalTokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web MVC 配置：CORS、静态资源（上传目录）、限流与 {@code /internal/**} 准入。
 * <p>
 * {@code /internal/entity/**} 是审批配置「数据绑定」取表清单 / 列元数据 / 真实行的入口，
 * 由 {@link InternalTokenInterceptor} 校验 X-Internal-Token（与其它服务同一套暗号）。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final BlogProperties blogProperties;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final InternalTokenInterceptor internalTokenInterceptor;

    @Value("${blog.upload.root:./uploads}")
    private String uploadRoot;

    @Value("${blog.upload.url-prefix:/uploads}")
    private String urlPrefix;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 本地磁盘上传目录映射为静态资源。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + Paths.get(uploadRoot).toAbsolutePath().normalize() + "/";
        registry.addResourceHandler(urlPrefix.endsWith("/") ? urlPrefix + "**" : urlPrefix + "/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/blog/public/**")
                .excludePathPatterns("/api/blog/public/theme/**");
        registry.addInterceptor(internalTokenInterceptor).addPathPatterns("/internal/**");
    }
}
