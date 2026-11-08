package com.laimengchao.sso.cms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** CMS 业务配置 */
@Data
@ConfigurationProperties(prefix = "cms")
public class CmsProperties {

    private Upload upload = new Upload();
    private Site site = new Site();

    @Data
    public static class Upload {
        private String root = "./uploads-cms";
        private String urlPrefix = "/api/cms/media/file";
        private int maxSizeMb = 10;
        private List<String> allowedExtensions = List.of(
                "jpg", "jpeg", "png", "gif", "webp", "bmp", "ico", "pdf", "mp4", "mp3", "zip");
    }

    @Data
    public static class Site {
        private String baseUrl = "http://localhost:9080";
    }
}
