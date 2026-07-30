package com.settlement.tickle.global.auth.handler;

import com.settlement.tickle.global.auth.jwt.repository.RedisRefreshTokenRepository;
import com.settlement.tickle.global.auth.jwt.service.JwtService;
import com.settlement.tickle.global.auth.jwt.util.JwtTokenType;
import com.settlement.tickle.global.auth.jwt.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@Qualifier("CustomLogoutHandler")
public class CustomLogoutHandler implements LogoutHandler {

    private final JwtUtil jwtUtil;
    private final JwtService jwtService;
    private final RedisRefreshTokenRepository refreshTokenRepository;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {

        // 쿠키에서 Refresh Token 추출
        String refreshToken = jwtService.getCookie(request, "refreshToken");

        if(refreshToken != null) {
            try {
                // 토큰 검증
                Claims claims = jwtUtil.getClaimsFromToken(refreshToken);

                // 타입 검증
                JwtTokenType tokenType = jwtUtil.getTokenType(claims);
                if(tokenType == JwtTokenType.REFRESH) {
                    refreshTokenRepository.deleteByUserId(jwtUtil.getUserId(claims));
                }

            } catch (ExpiredJwtException e) {
                // 만료, 위조된 토큰은 TTL 설정으로 인해 삭제되므로 쿠키만 삭제하고 패스
                log.warn("만료된 토큰입니다.", e);
            } catch (JwtException | IllegalArgumentException e) {
                log.warn("유효하지 않은 토큰입니다.", e);
            }
        }

        // 쿠키 삭제
        response.addCookie(jwtService.createCookie("refreshToken", "", 0));
    }

}
