package com.settlement.tickle.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.settlement.tickle.domain.member.entity.MemberRoleType;
import com.settlement.tickle.global.auth.filter.JwtFilter;
import com.settlement.tickle.global.auth.filter.LoginFilter;
import com.settlement.tickle.global.exception.ErrorCode;
import com.settlement.tickle.global.exception.ErrorResponse;
import com.settlement.tickle.global.response.ResultCode;
import com.settlement.tickle.global.response.ResultResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 비밀번호 BCrypt 암호화 Bean
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // MemberService(UserDetailsService) + PasswordEncoder 기반 인증을 수행하는 AuthenticationManager
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper, JwtFilter jwtFilter,
                                                   AuthenticationManager authenticationManager,
                                                   @Qualifier("CustomLogoutHandler") LogoutHandler customLogoutHandler,
                                                   @Qualifier("LoginSuccessHandler") AuthenticationSuccessHandler loginSuccessHandler,
                                                   @Qualifier("LoginFailureHandler") AuthenticationFailureHandler loginFailureHandler) throws Exception {

        // csrf 보안 필터 비활성화
        http.csrf(AbstractHttpConfigurer::disable);

        // cors 설정
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()));

        // 커스텀 로그아웃 핸들러 추가
        http.logout(logout -> logout
                .logoutUrl("/api/v1/auth/logout")
                .addLogoutHandler(customLogoutHandler)
                .logoutSuccessHandler((request, response, authentication) -> {
                    ResultCode resultCode = ResultCode.MEMBER_LOGOUT_SUCCESS;

                    response.setStatus(resultCode.getStatus().value());
                    response.setContentType("application/json;charset=UTF-8");

                    ResultResponse<?> resultResponse = ResultResponse.success(resultCode);
                    objectMapper.writeValue(response.getWriter(), resultResponse);
                }));

        // 기본 form 기반 인증 필터 비활성화
        http.formLogin(AbstractHttpConfigurer::disable);

        // 기본 basic 인증 필터 비활성화
        http.httpBasic(AbstractHttpConfigurer::disable);

        // 인가 설정
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/members/signup",
                        "/api/v1/auth/login",
                        "/api/v1/auth/logout",
                        "/api/v1/auth/reissue").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/members/*/exists").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/members/me").authenticated()
                // HOST 전용 API (추후 예정)
                // .requestMatchers("/api/v1/settlements/**").hasRole(MemberRoleType.HOST.name())
                .anyRequest().authenticated());

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
        // JwtFilter: LogoutFilter보다 먼저 실행되어야 로그아웃 요청에서도 Access Token 기반 인증 정보가 SecurityContext에 채워짐
        http.addFilterBefore(jwtFilter, LogoutFilter.class);

        // LoginFilter: JSON 바디(email/password)로 로그인 수행, 기본 form 로그인 필터(UsernamePasswordAuthenticationFilter) 자리를 대체
        LoginFilter loginFilter = new LoginFilter(authenticationManager, loginSuccessHandler, loginFailureHandler);
        http.addFilterAt(loginFilter, UsernamePasswordAuthenticationFilter.class);

        // 세션 필터 STATELESS
        http.sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }

    // cors
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        // 허용 메서드
        // CORS는 실제 요청 전에 Preflight 요청(OPTIONS) 날리므로 포함 필수
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // 모든 헤더 허용
        configuration.setAllowedHeaders(List.of("*"));
        // 인증정보 포함 (JWT / 쿠키)
        configuration.setAllowCredentials(true);
        // 노출할 헤더 (JWT Authorization)
        // 브라우저는 기본적으로 Authorization 헤더를 JS에서 못 읽으므로 설정
        configuration.setExposedHeaders(List.of("Authorization", "Set-Cookie"));
        configuration.setMaxAge(3600L);

        // 해당 url에 대해 config CORS 정책 사용
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
