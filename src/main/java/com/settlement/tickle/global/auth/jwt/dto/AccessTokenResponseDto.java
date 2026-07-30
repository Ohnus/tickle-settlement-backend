package com.settlement.tickle.global.auth.jwt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 시 액세스 토큰 발급")
public record AccessTokenResponseDto(
        @Schema(description = "액세스 토큰(JWT)", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature")
        String accessToken
) {
}
