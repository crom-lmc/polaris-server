package com.laimengchao.sso.blog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.blog.common.BlogErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.blog.security.SsoHeaderAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 安全配置：只做「有没有登录」这一层，细粒度权限交给 {@code @Permit}。
 * <p>
 * 为什么不再用 URL 级 RBAC：博客原来的角色只有 admin / author 两档，而账号中心是
 * 按钮级权限码。如果这里继续按 URL 划 admin/author，就会出现「同一份权限在两处维护」
 * 的分裂——最终必然走偏。现在这里只拦「未登录」，每个接口能做什么由方法上的
 * {@code @Permit("article:edit")} 之类决定，权限的唯一真相源是账号中心。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SsoHeaderAuthFilter ssoHeaderAuthFilter;
    private final ObjectMapper objectMapper;

    /** 完全公开：前台读者接口、单点换票、上传后的静态文件、接口文档 */
    private static final String[] PUBLIC_URLS = {
            "/api/blog/public/**",
            // 换票：调用时用户还没有令牌，凭一次性 ticket 换；安全边界由 ticket 保证
            "/api/blog/sso/**",
            // 服务间调用：不带用户令牌，改由 InternalTokenInterceptor 校验 X-Internal-Token
            "/internal/**",
            "/uploads/**",
            "/actuator/health",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/doc.html",
            "/webjars/**",
            "/favicon.ico",
            "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, e) ->
                                writeJson(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        .anyRequest().authenticated())
                // 放在 SecurityContextHolderFilter 之后：它负责在请求结束时清理上下文，
                // 我们写在它后面才能保证清理动作覆盖到本过滤器设置的身份
                .addFilterAfter(ssoHeaderAuthFilter, SecurityContextHolderFilter.class);
        return http.build();
    }

    private void writeJson(HttpServletResponse response, int status, ErrorCode code) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(code)));
    }
}
