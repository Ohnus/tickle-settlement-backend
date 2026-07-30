package com.settlement.tickle.global.auth.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.settlement.tickle.global.auth.custom.CustomUserDetails;
import com.settlement.tickle.global.auth.jwt.dto.AccessTokenResponseDto;
import com.settlement.tickle.global.auth.jwt.repository.RedisRefreshTokenRepository;
import com.settlement.tickle.global.auth.jwt.service.JwtService;
import com.settlement.tickle.global.auth.jwt.util.JwtTokenType;
import com.settlement.tickle.global.auth.jwt.util.JwtUtil;
import com.settlement.tickle.global.response.ResultCode;
import com.settlement.tickle.global.response.ResultResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Qualifier("LoginSuccessHandler")
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final JwtService jwtService;
    private final RedisRefreshTokenRepository refreshTokenRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {

        // authentication 객체에서 유저 정보 취득
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        Long userId = userDetails.getUserId();
        String email = userDetails.getUsername();
        String role = userDetails.getAuthorities().iterator().next().getAuthority();

        // JWT 생성
        String accessToken = jwtUtil.createJwt(userId, email, role, JwtTokenType.ACCESS);
        String refreshToken = jwtUtil.createJwt(userId, email, role, JwtTokenType.REFRESH);

        // Refresh Token Redis 저장
        refreshTokenRepository.save(userId, refreshToken);

        // Refresh Token 쿠키 저장
        Cookie refreshTokenCookie = jwtService.createCookie("refreshToken", refreshToken, 24 * 60 * 60);
        response.addCookie(refreshTokenCookie);

        // Access Token Body 응답
        AccessTokenResponseDto dto = new AccessTokenResponseDto(accessToken);
        ResultResponse<?> resultResponse = ResultResponse.of(ResultCode.MEMBER_LOGIN_SUCCESS, dto);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), resultResponse);
    }
}
