package com.settlement.tickle.domain.member.controller;

import com.settlement.tickle.global.auth.jwt.dto.AccessTokenResponseDto;
import com.settlement.tickle.global.auth.jwt.service.JwtService;
import com.settlement.tickle.global.exception.BusinessException;
import com.settlement.tickle.global.exception.ErrorCode;
import com.settlement.tickle.global.exception.ErrorResponse;
import com.settlement.tickle.global.response.ResultCode;
import com.settlement.tickle.global.response.ResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "인증", description = "로그인/로그아웃/토큰 재발급 등 인증 관련 API (로그인/로그아웃 실제 처리는 LoginFilter, LogoutFilter가 담당)")
public class AuthController {

    private final JwtService jwtService;

    @Operation(summary = "토큰 재발급", description = "Refresh Token으로 Access Token을 재발급합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재발급 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 Refresh Token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/reissue")
    public ResultResponse<AccessTokenResponseDto> reissue(
            HttpServletResponse response,
            @CookieValue(value = "refreshToken", required = false) Optional<String> refreshToken
            )
    {

        AccessTokenResponseDto dto = jwtService.reissueTokens(response, refreshToken
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND)));
        return ResultResponse.of(ResultCode.TOKEN_REISSUE_SUCCESS, dto);
    }
}
