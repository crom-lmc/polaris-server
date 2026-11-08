package com.laimengchao.sso.cms.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * CORS、上传目录静态映射与 /internal/** 准入。
 * <p>
 * /api/cms/media/file/** 直接映射本地磁盘，这样媒体文件经网关路由 /api/cms/** 就能访问；
 * /internal/** 由内部令牌拦截器把关（settings 回调审批结果走这里）。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final CmsProperties cmsProperties;
    private final com.laimengchao.sso.cms.security.InternalTokenInterceptor internalTokenInterceptor;

    @Value("${cms.upload.root:./uploads-cms}")
    private String uploadRoot;

    @Value("${cms.upload.url-prefix:/api/cms/media/file}")
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

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalTokenInterceptor).addPathPatterns("/internal/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + Paths.get(uploadRoot).toAbsolutePath().normalize() + "/";
        registry.addResourceHandler(urlPrefix.endsWith("/") ? urlPrefix + "**" : urlPrefix + "/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}
