package com.laimengchao.sso.job.client.executor;

import com.laimengchao.sso.job.client.dto.HandlerDefinition;
import com.laimengchao.sso.job.client.dto.JobAck;
import com.laimengchao.sso.job.client.dto.JobDefinitionsRequest;
import com.laimengchao.sso.job.client.dto.JobHeartbeatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 注册与心跳上报：启动后立即上报 Handler 清单，之后每 30 秒 ping 一次；关闭时上报离线。
 * <p>
 * 用 {@link RestClient} 而不是 Feign：网关是 WebFlux，Feign 依赖 Servlet 的
 * {@code HttpMessageConverters}，在 reactive 应用里装配不起来。
 * <p>
 * 上报失败不阻断应用启动——调度中心是旁路能力，不能反过来拖垮业务应用。
 * 单线程 daemon，无需应用开启 @EnableScheduling。
 */
@Slf4j
public class JobRegistryReporter implements InitializingBean, DisposableBean {

    private static final String DEFINITIONS_PATH = "/internal/jobs/definitions";
    private static final String PING_PATH = "/internal/jobs/ping";
    private static final String OFFLINE_PATH = "/internal/jobs/offline";

    private final JobDispatcher dispatcher;
    private final Environment environment;
    private final RestClient client;
    private final String appCode;
    private final String version;
    private final String token;
    private final int intervalSeconds;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "job-registry-reporter");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean definitionsReported;

    public JobRegistryReporter(JobDispatcher dispatcher, Environment environment) {
        this.dispatcher = dispatcher;
        this.environment = environment;
        this.appCode = environment.getProperty("job.executor.app-code",
                environment.getProperty("spring.application.name", "unknown"));
        this.version = environment.getProperty("job.executor.version", "");
        this.token = environment.getProperty("job.client.internal-token",
                environment.getProperty("SSO_INTERNAL_TOKEN", ""));
        this.intervalSeconds = parsePositiveInt(
                environment.getProperty("job.executor.heartbeat-interval-seconds", "30"), 30);
        this.client = buildClient(environment);
    }

    @Override
    public void afterPropertiesSet() {
        scheduler.scheduleWithFixedDelay(this::heartbeatSafely, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    private void heartbeatSafely() {
        try {
            if (!definitionsReported) {
                JobAck ack = post(DEFINITIONS_PATH, buildDefinitions());
                if (ack == null || !ack.ok()) {
                    log.warn("上报 Handler 清单失败：app={} address={} message={}", appCode, address(),
                            ack == null ? "无响应" : ack.getMessage());
                    return;
                }
                definitionsReported = true;
                log.info("已向调度中心上报 Handler 清单：app={} address={} handlers={}",
                        appCode, address(), dispatcher.names());
                return;
            }
            JobAck ack = post(PING_PATH, new JobHeartbeatRequest(appCode, address()));
            if (ack == null || !ack.ok()) {
                // 中心可能重启过，下一轮重新上报清单
                definitionsReported = false;
                log.warn("调度中心心跳失败，将重新上报清单：app={} message={}", appCode,
                        ack == null ? "无响应" : ack.getMessage());
            }
        } catch (Exception e) {
            definitionsReported = false;
            log.warn("调度中心不可达：app={} address={} reason={}", appCode, address(), e.getMessage());
        }
    }

    private JobAck post(String path, Object body) {
        return client.post()
                .uri(path)
                .header("X-Internal-Token", token == null ? "" : token.trim())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JobAck.class);
    }

    private JobDefinitionsRequest buildDefinitions() {
        List<HandlerDefinition> handlers = dispatcher.names().stream()
                .map(name -> new HandlerDefinition(name, null))
                .toList();
        JobDefinitionsRequest request = new JobDefinitionsRequest();
        request.setAppCode(appCode);
        request.setAddress(address());
        request.setVersion(version);
        request.setHandlers(handlers);
        return request;
    }

    private String address() {
        String configured = environment.getProperty("job.executor.address", "");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        String port = environment.getProperty("local.server.port",
                environment.getProperty("server.port", ""));
        try {
            String host = InetAddress.getLocalHost().getHostAddress();
            return port == null || port.isBlank() ? host : host + ":" + port;
        } catch (Exception e) {
            return "unknown:" + ProcessHandle.current().pid();
        }
    }

    @Override
    public void destroy() {
        try {
            if (definitionsReported) {
                post(OFFLINE_PATH, new JobHeartbeatRequest(appCode, address()));
            }
        } catch (Exception e) {
            log.debug("上报下线失败（忽略）：{}", e.getMessage());
        }
        scheduler.shutdownNow();
    }

    private static RestClient buildClient(Environment environment) {
        String baseUrl = environment.getProperty("job.client.center-url", "http://127.0.0.1:9008").trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    private static int parsePositiveInt(String raw, int defaultValue) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
