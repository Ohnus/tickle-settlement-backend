package com.settlement.tickle.global.auth.jwt.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/* ===============================
*  JWT Refresh Token 관리 구현체
*  Key = refresh:user:userId
*  Duration = 1일
*  =============================== */
@Repository
@RequiredArgsConstructor
public class RedisRefreshTokenRepository implements RefreshTokenRepository {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(
                key(userId),
                refreshToken,
                Duration.ofDays(1)
        );
    }

    @Override
    public Optional<String> findByUserId(Long userId) {
        return Optional.ofNullable(
                redisTemplate.opsForValue().get(
                        key(userId)
                )
        );
    }

    @Override
    public void deleteByUserId(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return "refresh:user:" + userId;
    }
}
