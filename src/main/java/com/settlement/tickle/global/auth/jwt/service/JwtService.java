package com.settlement.tickle.global.auth.jwt.service;

import com.settlement.tickle.global.auth.jwt.dto.AccessTokenResponseDto;
import com.settlement.tickle.global.auth.jwt.repository.RedisRefreshTokenRepository;
import com.settlement.tickle.global.auth.jwt.util.JwtTokenType;
import com.settlement.tickle.global.auth.jwt.util.JwtUtil;
import com.settlement.tickle.global.exception.BusinessException;
import com.settlement.tickle.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtUtil jwtUtil;
    private final RedisRefreshTokenRepository refreshTokenRepository;

    public AccessTokenResponseDto reissueTokens(HttpServletResponse response, String refreshToken) {

        try {
            // JWT 파싱 및 토큰 만료, 형식, 위조, null 등 검증
            Claims claims = jwtUtil.getClaimsFromToken(refreshToken);

            // 토큰 타입 검증
            JwtTokenType tokenType = jwtUtil.getTokenType(claims);
            if (tokenType != JwtTokenType.REFRESH) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }

            // 정보 추출
            Long id = jwtUtil.getUserId(claims);
            String email = jwtUtil.getEmail(claims);
            String role = jwtUtil.getRole(claims);

            // Redis 화이트리스트 검증(id로 조회 검증 + 토큰 자체 검증)
            String savedRefreshToken = refreshTokenRepository.findByUserId(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
            if (!savedRefreshToken.equals(refreshToken)) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }

            // 새 토큰 생성
            String newAccessToken = jwtUtil.createJwt(id, email, role, JwtTokenType.ACCESS);
            String newRefreshToken = jwtUtil.createJwt(id, email, role, JwtTokenType.REFRESH);

            // 기존 Refresh Token 삭제 후 신규 저장
            refreshTokenRepository.deleteByUserId(id);
            refreshTokenRepository.save(id, newRefreshToken);

            // 기존 쿠키 덮어쓰기
            Cookie refreshCookie = createCookie("refreshToken", newRefreshToken, 24 * 60 * 60);
            response.addCookie(refreshCookie);

            // Access Token Body 응답
            return new AccessTokenResponseDto(newAccessToken);

        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    // 쿠키 생성
    public Cookie createCookie(String key, String value, int maxAge) {
        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(maxAge); // 쿠키 TTL
        cookie.setPath("/"); // 쿠키 적용될 범위
        cookie.setHttpOnly(true); // JS로 해당 쿠키 접근 못하도록 설정
        // cookie.setSecure(true); // Https일 경우 설정

        return cookie;
    }

    // 쿠키 추출
    public String getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();

        if(cookies == null) {
            return null;
        }

        for(Cookie cookie : cookies) {
            if(name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }
}
