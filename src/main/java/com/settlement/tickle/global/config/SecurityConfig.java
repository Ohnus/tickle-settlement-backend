package com.settlement.tickle.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.settlement.tickle.global.auth.filter.JwtFilter;
import com.settlement.tickle.global.exception.ErrorCode;
import com.settlement.tickle.global.exception.ErrorResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 비밀번호 BCrypt 암호화 Bean
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper, JwtFilter jwtFilter) throws Exception {

        // csrf 보안 필터 비활성화
        http.csrf(AbstractHttpConfigurer::disable);

        // cors 설정

        // 커스텀 로그아웃 핸들러 추가

        // 기본 form 기반 인증 필터 비활성화
        http.formLogin(AbstractHttpConfigurer::disable);

        // 기본 basic 인증 필터 비활성화
        http.httpBasic(AbstractHttpConfigurer::disable);

        // 인가 설정
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/members/me").authenticated()
                .anyRequest().permitAll());

        // 로그인 이후 API 요청 시 토큰이 없거나 권한이 부족할 때 401, 403
        http.exceptionHandling(e -> e
                .authenticationEntryPoint((request, response, authException) -> {
                    ErrorCode errorCode = ErrorCode.UNAUTHORIZED;

                    response.setStatus(errorCode.getStatus().value());
                    response.setContentType("application/json;charset=UTF-8");

                    ErrorResponse errorResponse = ErrorResponse.of(errorCode);
                    objectMapper.writeValue(response.getWriter(), errorResponse);
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    ErrorCode errorCode = ErrorCode.FORBIDDEN;

                    response.setStatus(errorCode.getStatus().value());
                    response.setContentType("application/json;charset=UTF-8");

                    ErrorResponse errorResponse = ErrorResponse.of(errorCode);
                    objectMapper.writeValue(response.getWriter(), errorResponse);
                })
        );

        // 커스텀 필터 추가
        // JwtFilter: Authorization 헤더의 Access Token을 검증해 SecurityContext에 CustomUserPrincipal을 채움
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        // LoginFilter(Success/Failure Handler 포함)는 아직 미등록 — /login으로 토큰을 발급받는 흐름 자체가 별도 작업으로 남아있음

        // 세션 필터 STATELESS
        http.sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
}
