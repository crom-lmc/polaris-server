package com.laimengchao.sso.blog.config;

import com.laimengchao.sso.blog.common.ratelimit.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web MVC 配置：静态资源（上传目录）与读者端限流。
 * <p>
 * CORS 与 {@code /internal/**} 准入统一由 common 的 {@code CommonSecurityWebConfig} 负责。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Value("${blog.upload.root:./uploads}")
    private String uploadRoot;

    @Value("${blog.upload.url-prefix:/uploads}")
    private String urlPrefix;

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
    }
}
