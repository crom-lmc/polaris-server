package com.laimengchao.sso.cms.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 上传目录静态映射。
 * <p>
 * CORS 与 {@code /internal/**} 准入统一由 common 的 {@code CommonSecurityWebConfig} 负责，
 * 本服务不再重复实现。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final CmsProperties cmsProperties;

    @Value("${cms.upload.root:./uploads-cms}")
    private String uploadRoot;

    @Value("${cms.upload.url-prefix:/api/cms/media/file}")
    private String urlPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + Paths.get(uploadRoot).toAbsolutePath().normalize() + "/";
        registry.addResourceHandler(urlPrefix.endsWith("/") ? urlPrefix + "**" : urlPrefix + "/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}
