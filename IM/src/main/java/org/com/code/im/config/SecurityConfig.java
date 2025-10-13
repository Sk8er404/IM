package org.com.code.im.config;

import org.com.code.im.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.Collections;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig{
    @Autowired
    private UserMapper userMapper;

    @Value("${spring.elasticsearch.allowedIpAddress}")
    private String allowedIpAddress;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Autowired
    private AuthenticationConfiguration authenticationConfiguration;

    @Bean
    public AuthenticationManager authenticationManager() throws Exception{
        AuthenticationManager authenticationManager = authenticationConfiguration.getAuthenticationManager();
        return authenticationManager;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .formLogin(form -> form.disable())//禁用默认登录页面
                .logout(config->config.disable())//禁用默认登出页面
                .httpBasic(httpBasic -> httpBasic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable()) // 禁用 CSRF 保护（仅在开发环境中使用，生产环境应启用）
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/user/signUp").permitAll()
                        .requestMatchers("/api/user/login").permitAll() // 确保登录页面可访问
                        .requestMatchers("/api/es/dict/update").access(new WebExpressionAuthorizationManager("hasIpAddress('" + allowedIpAddress + "')"))
                        .requestMatchers("/api/video/selectAllVideoWaitToReview").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/video/updateVideoReviewStatus").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/sensitiveDict/addWord").hasAuthority("ROLE_ADMIN")
                        .anyRequest().authenticated() // 其他所有请求都需要认证
                )
                //添加JWT认证过滤器
                .addFilterBefore(new JwtAuthenticationFilter(userMapper), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
