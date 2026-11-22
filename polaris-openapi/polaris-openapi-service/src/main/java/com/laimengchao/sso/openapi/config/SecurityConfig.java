package com.laimengchao.sso.openapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.openapi.security.admin.SsoHeaderAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 开放平台三条通道的安全边界：
 * <ul>
 *   <li>第三方 /api/open/token、/api/open/v1/** → 由 OpenapiAuthFilter 做 Bearer/签名鉴权；</li>
 *   <li>出站 /api/open/outbound/** → 由 OpenapiAuthFilter 做 X-Internal-Token 鉴权；</li>
 *   <li>管理端 /api/open/admin/** → 由 SsoHeaderAuthFilter 读网关注入头建登录态，这里只拦未登录，细粒度交给 @Permit。</li>
 * </ul>
 * 前两类必须 permitAll（鉴权已在前置过滤器完成）；只有管理端要求 authenticated。
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SsoHeaderAuthFilter ssoHeaderAuthFilter;
    private final ObjectMapper objectMapper;

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
                        // 管理端：必须带网关签发的用户身份（网关注入 X-Account-* 头）
                        .requestMatchers("/api/open/admin/**").authenticated()
                        // 第三方 + 出站 + 健康检查：鉴权由 OpenapiAuthFilter 承担
                        .anyRequest().permitAll())
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
