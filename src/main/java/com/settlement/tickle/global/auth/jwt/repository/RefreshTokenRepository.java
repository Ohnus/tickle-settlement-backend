package com.settlement.tickle.global.auth.jwt.repository;

import java.util.Optional;

/* ===============================
*  JWT Refresh Token 관리 인터페이스
*  =============================== */
public interface RefreshTokenRepository {

    void save(Long userId, String refreshToken);

    Optional<String> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
