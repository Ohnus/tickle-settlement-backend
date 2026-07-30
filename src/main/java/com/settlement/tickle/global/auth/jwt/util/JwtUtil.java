package com.settlement.tickle.global.auth.jwt.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final JwtParser jwtParser;
    private final long accessTokenExpiresIn;
    private final long refreshTokenExpiresIn;

    public JwtUtil(@Value("${app.jwt.secret-key}") String key,
                   @Value("${app.jwt.access-expiration-time}") long accessTokenExpiresIn,
                   @Value("${app.jwt.refresh-expiration-time}") long refreshTokenExpiresIn) {
        this.secretKey = Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8));
        this.jwtParser = Jwts.parser().verifyWith(secretKey).build();
        this.accessTokenExpiresIn = accessTokenExpiresIn;
        this.refreshTokenExpiresIn = refreshTokenExpiresIn;
    }

    // JWT 검증(위조, 만료 여부)
    public Claims getClaimsFromToken(String token) {
        return jwtParser.parseSignedClaims(token).getPayload();
    }

    // id 획득
    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    // email 획득
    public String getEmail(Claims claims) {
        return claims.get("email", String.class);
    }

    // Role 획득
    public String getRole(Claims claims) {
        return claims.get("role", String.class);
    }

    // JWT 토큰 타입 획득
    public JwtTokenType getTokenType(Claims claims) {
        String tokenType = claims.get("type", String.class);
        return JwtTokenType.valueOf(tokenType.toUpperCase());
    }

    // JWT 생성
    public String createJwt(Long id, String email, String role, JwtTokenType tokenType) {

        long now = System.currentTimeMillis();
        long expiresIn = tokenType == JwtTokenType.ACCESS ? accessTokenExpiresIn : refreshTokenExpiresIn;

        return Jwts.builder()
                .subject(String.valueOf(id))
                .claim("email", email)
                .claim("role", role)
                .claim("type", tokenType.name())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiresIn))
                .signWith(secretKey)
                .compact();
    }
}
