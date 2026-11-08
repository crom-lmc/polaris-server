package com.laimengchao.sso.common.crypto;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动早期把所有 {@code ENC(...)} 配置统一解密。
 * <p>
 * 为什么必须在这一层做：DB 口令、JWT 私钥这类值要在数据源、{@code JwtService} 等 Bean
 * 创建之前就是明文，而它们分散在 HikariCP、MyBatis 等第三方自动配置里，
 * 逐个加解密钩子既不可靠也容易漏。直接改 {@link PropertySource} 的底层 Map，
 * 后面任何读取路径（{@code @Value}、{@code @ConfigurationProperties}、占位符解析）拿到的都是明文。
 * <p>
 * 执行时机：order 取 {@code LOWEST_PRECEDENCE - 100}，排在
 * {@code ConfigDataEnvironmentPostProcessor}（HIGHEST_PRECEDENCE + 10）之后，
 * 保证 Nacos / {@code spring.config.import} 拉回的配置已经在 Environment 里。
 * <p>
 * 安全取舍：<b>发现密文却没有主密钥时直接启动失败</b>，绝不把 {@code ENC(...)} 当明文用——
 * 静默失败会演变成「用密文当数据库口令去连库」这种极难排查的问题。
 */
public class EncryptedPropertyEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** 主密钥环境变量（Base64 或 16/24/32 字节原始字符串） */
    public static final String KEY_PROPERTY = "SSO_CONF_KEY";
    /** 主密钥文件（容器挂 secret 时更方便，文件内容即密钥） */
    public static final String KEY_FILE_PROPERTY = "SSO_CONF_KEY_FILE";

    private static final Log log = LogFactory.getLog(EncryptedPropertyEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 先扫一遍，确认有没有密文：没有密文就完全不碰主密钥，不影响未启用加密的环境
        Map<MapPropertySource, List<String>> encrypted = new LinkedHashMap<>();
        int total = 0;
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!(source instanceof MapPropertySource mapSource)) {
                continue;
            }
            List<String> keys = null;
            for (Map.Entry<String, Object> entry : mapSource.getSource().entrySet()) {
                if (entry.getValue() instanceof String value && SecretCipher.isEncrypted(value)) {
                    if (keys == null) {
                        keys = new ArrayList<>();
                    }
                    keys.add(entry.getKey());
                }
            }
            if (keys != null) {
                encrypted.put(mapSource, keys);
                total += keys.size();
            }
        }
        if (total == 0) {
            return;
        }

        byte[] masterKey = resolveMasterKey(environment, total);
        List<String> decryptedNames = new ArrayList<>();
        for (Map.Entry<MapPropertySource, List<String>> entry : encrypted.entrySet()) {
            MapPropertySource source = entry.getKey();
            for (String key : entry.getValue()) {
                Object raw = source.getSource().get(key);
                if (!(raw instanceof String value)) {
                    continue;
                }
                // 只记录配置项名，绝不打印明文或密文，避免日志变成新的泄露渠道
                source.getSource().put(key, SecretCipher.decrypt(value, masterKey));
                decryptedNames.add(key);
            }
        }

        // 这一阶段 LoggingApplicationListener 尚未初始化，log.info 会被丢弃，
        // 所以汇总信息直接写 stdout —— 启动时能一眼确认「配置确实解密过了」。
        String summary = "[SSO] 已解密 " + decryptedNames.size() + " 个配置项：" + String.join(", ", decryptedNames);
        System.out.println(summary);
        log.info(summary);
    }

    private byte[] resolveMasterKey(ConfigurableEnvironment environment, int expectedCount) {
        String raw = environment.getProperty(KEY_PROPERTY);
        String origin = "环境变量 " + KEY_PROPERTY;

        if (raw == null || raw.isBlank()) {
            String file = environment.getProperty(KEY_FILE_PROPERTY);
            if (file != null && !file.isBlank()) {
                origin = "密钥文件 " + file;
                try {
                    raw = Files.readString(Path.of(file.trim()), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException("读取主密钥文件失败：" + file, e);
                }
            }
        }

        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("""

                    启动中止：检测到 %d 个加密配置项（ENC(...)），但没有提供主密钥。
                    请任选一种方式提供：
                      1) 设置环境变量 %s（Base64 字符串）
                      2) 设置环境变量 %s 指向存放密钥的文件
                    生成新密钥：java tools/SecretTool.java genkey
                    加密某个明文：java tools/SecretTool.java encrypt '<明文>'
                    """.formatted(expectedCount, KEY_PROPERTY, KEY_FILE_PROPERTY));
        }

        try {
            byte[] key = SecretCipher.parseKey(raw);
            System.out.println("[SSO] 主密钥已加载（来源：" + origin + "）");
            log.info("主密钥已加载（来源：" + origin + "）");
            return key;
        } catch (RuntimeException e) {
            throw new IllegalStateException("主密钥无效（来源：" + origin + "）：" + e.getMessage(), e);
        }
    }

    /**
     * 必须晚于 ConfigDataEnvironmentPostProcessor（它负责加载 Nacos / spring.config.import），
     * 否则会看不到 Nacos 拉回的配置。
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
}
