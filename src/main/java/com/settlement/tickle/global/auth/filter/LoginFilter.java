package com.settlement.tickle.global.auth.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/* =========================
*  JSON 기반 로그인 수행 필터 (POST /login)
*  기본 로그인 필터인 UsernamePasswordAuthenticationFilter 참조하여 작성
*  ========================= */
public class LoginFilter extends AbstractAuthenticationProcessingFilter {

    public static final String SPRING_SECURITY_FORM_USERNAME_KEY = "username";
    public static final String SPRING_SECURITY_FORM_PASSWORD_KEY = "password";
    private static final RequestMatcher DEFAULT_ANT_PATH_REQUEST_MATCHER = PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.POST, "/api/v1/auth/login");
    private String usernameParameter =  SPRING_SECURITY_FORM_USERNAME_KEY;
    private String passwordParameter =  SPRING_SECURITY_FORM_PASSWORD_KEY;
    private final AuthenticationSuccessHandler authenticationSuccessHandler;
    private final AuthenticationFailureHandler authenticationFailureHandler;

    public LoginFilter(AuthenticationManager authenticationManager,
                       AuthenticationSuccessHandler authenticationSuccessHandler,
                       AuthenticationFailureHandler authenticationFailureHandler) {
        super(DEFAULT_ANT_PATH_REQUEST_MATCHER, authenticationManager);
        this.authenticationSuccessHandler = authenticationSuccessHandler;
        this.authenticationFailureHandler = authenticationFailureHandler;
        setAuthenticationSuccessHandler(authenticationSuccessHandler);
        setAuthenticationFailureHandler(authenticationFailureHandler);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {

        // 요청 메서드 POST 이외엔 로그인 실패 핸들러에서 400(Bad Request) 응답
        if(!request.getMethod().equals("POST")) {
            throw new AuthenticationServiceException("지원하지 않는 인증 요청 메서드입니다. : " + request.getMethod());
        }

        Map<String, String> loginMap;

        // JSON 깨졌거나 읽기 실패 시 로그인 실패 핸들러에서 400(Bad Request) 응답
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            ServletInputStream inputStream = request.getInputStream();
            String messageBody = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            loginMap = objectMapper.readValue(messageBody, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new AuthenticationServiceException("로그인 요청 형식이 올바르지 않습니다.", e);
        }

        String email = loginMap.get("email");
        email = email != null ? email.trim() : "";
        String password = loginMap.get("password");
        password = password != null ? password.trim() : "";

        // 인증 전 토큰 생성(Authentication 객체 생성)
        UsernamePasswordAuthenticationToken authRequest = UsernamePasswordAuthenticationToken.unauthenticated(
                email, password);
        // 요청 정보 추가
        setDetails(request, authRequest);

        // AuthenticationManager 인증 수행
        // DaoAuthenticationProvider가 UserService.loadUserByUsername() 호출 및 DB에서 사용자 정보를 조회 및 UserDetails 반환
        // DaoAuthenticationProvider가 입력 평문 비밀번호와 DB 암호화 비밀번호 비교
        // authenticate()가 성공하면 인증된 Authentication 객체를 반환(인증된 사용자 정보, Role, 인증 상태)
        // 성공 -> LoginSuccessHandler 호출 및 JWT 발급 / 실패 -> LoginFailureHandler 호출
        return this.getAuthenticationManager().authenticate(authRequest);
    }

    protected void setDetails(HttpServletRequest request, UsernamePasswordAuthenticationToken authRequest) {
        authRequest.setDetails(this.authenticationDetailsSource.buildDetails(request));
    }

}
