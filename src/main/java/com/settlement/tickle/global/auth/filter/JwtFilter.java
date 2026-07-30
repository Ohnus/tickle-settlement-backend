package com.settlement.tickle.global.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.settlement.tickle.global.auth.custom.CustomUserPrincipal;
import com.settlement.tickle.global.auth.jwt.util.JwtTokenType;
import com.settlement.tickle.global.auth.jwt.util.JwtUtil;
import com.settlement.tickle.global.exception.ErrorCode;
import com.settlement.tickle.global.exception.ErrorResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 헤더 추출
        String authorization = request.getHeader("Authorization");

        if(authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 토큰 추출
        String accessToken = authorization.substring(7);

        try {
            // 이미 인증 객체 있는 경우 통과
            if(SecurityContextHolder.getContext().getAuthentication() != null) {
                filterChain.doFilter(request, response);
                return;
            }

            // 토큰 검증
            Claims claims = jwtUtil.getClaimsFromToken(accessToken);

            // 토큰 타입 검증
            JwtTokenType tokenType = jwtUtil.getTokenType(claims);
            if(tokenType != JwtTokenType.ACCESS) {
                sendUnauthorizedResponse(response, ErrorCode.INVALID_TOKEN);
                return;
            }

            // 유저 정보 추출 및 인증 객체 생성
            CustomUserPrincipal principal = new CustomUserPrincipal(
                    jwtUtil.getUserId(claims),
                    jwtUtil.getEmail(claims),
                    List.of(new SimpleGrantedAuthority(jwtUtil.getRole(claims)))
            );
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

            // SecurityContextHolder 등록
            SecurityContextHolder.getContext().setAuthentication(authToken);
        } catch (ExpiredJwtException e) {
            sendUnauthorizedResponse(response, ErrorCode.EXPIRED_ACCESS_TOKEN);
            return;
        } catch (JwtException | IllegalArgumentException e) {
            sendUnauthorizedResponse(response, ErrorCode.INVALID_TOKEN);
            return;
        }

        // 다음 필터로 패스
        filterChain.doFilter(request, response);
    }

    // 에러 응답
    private void sendUnauthorizedResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");

        ErrorResponse errorResponse = ErrorResponse.of(errorCode);
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
