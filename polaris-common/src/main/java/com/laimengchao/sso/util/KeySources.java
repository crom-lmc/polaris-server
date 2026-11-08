package com.laimengchao.sso.util;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 密钥来源解析：把配置里的一个字符串统一转成可读的 PEM 流。
 * <p>
 * 支持三种写法，目的是让密钥既能「本地开发放文件」，也能「生产从 Nacos 下发」：
 * <ol>
 *   <li>内联 PEM 文本——配置中心里直接写 {@code -----BEGIN PRIVATE KEY-----} 开头的多行文本
 *       （Nacos 中通常再套一层 {@code ENC(...)}，启动时已由解密器还原成 PEM）；</li>
 *   <li>带前缀的地址：{@code classpath:keys/jwt-private.pem}、{@code file:/run/secrets/key.pem}、
 *       {@code https://...}；</li>
 *   <li>裸路径：绝对路径按文件读，相对路径先按文件读、不存在再退回 classpath。</li>
 * </ol>
 * 调用方负责关闭返回的流。
 */
public final class KeySources {

    private static final String PEM_HEADER = "-----BEGIN";

    private KeySources() {
    }

    /** 把配置值解析成 PEM 输入流；解析不了时抛出带明确提示的异常。 */
    public static InputStream open(String spec) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalStateException(
                    "密钥未配置：请检查 Nacos 中的 sso.auth.private-key / public-key，"
                            + "本地调试可填 classpath:keys/xxx.pem");
        }
        String text = spec.trim();

        // 1) 内联 PEM：配置中心最常见的形式
        if (text.contains(PEM_HEADER)) {
            // YAML 块标量会带来首尾空行与缩进，这里统一成单个换行分隔的紧凑文本
            String normalized = text.replace("\r\n", "\n").replace("\r", "\n").trim() + "\n";
            return new ByteArrayInputStream(normalized.getBytes(StandardCharsets.UTF_8));
        }

        // 2) 带前缀的地址
        if (text.startsWith("classpath:")) {
            return openResource(new ClassPathResource(text.substring("classpath:".length())));
        }
        if (text.startsWith("file:") || text.startsWith("http://") || text.startsWith("https://")) {
            try {
                return openResource(new UrlResource(text));
            } catch (IOException e) {
                throw new IllegalStateException("密钥地址无效：" + text, e);
            }
        }

        // 3) 裸路径：先当文件，再退回 classpath（兼容历史上 classpath:keys 的写法）
        Path path = Path.of(text);
        if (Files.isReadable(path)) {
            return openResource(new FileSystemResource(path));
        }
        ClassPathResource classPathResource = new ClassPathResource(text);
        if (classPathResource.exists()) {
            return openResource(classPathResource);
        }
        throw new IllegalStateException("找不到密钥文件：" + text + "（既不是可读文件，也不在 classpath 上）");
    }

    private static InputStream openResource(Resource resource) {
        try {
            return resource.getInputStream();
        } catch (IOException e) {
            throw new IllegalStateException("读取密钥失败：" + resource, e);
        }
    }
}
