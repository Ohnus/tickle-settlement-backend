package com.settlement.tickle.domain.member.controller;

import com.settlement.tickle.domain.member.dto.request.MemberLoginDummyRequestDto;
import com.settlement.tickle.global.auth.jwt.dto.AccessTokenResponseDto;
import com.settlement.tickle.global.exception.ErrorResponse;
import com.settlement.tickle.global.response.ResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "인증", description = "실제 처리는 LoginFilter, LogoutFilter가 담당")
public class AuthDummyController {

    @Operation(summary = "로그인", description = "이메일/비밀번호로 로그인합니다. Access Token은 응답 바디로, Refresh Token은 HttpOnly 쿠키로 발급됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(schema = @Schema(implementation = AccessTokenResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "요청 형식이 올바르지 않음(JSON 파싱 실패 등)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/login")
    public ResultResponse<AccessTokenResponseDto> login(@RequestBody MemberLoginDummyRequestDto loginDummyRequestDto) {
        throw new UnsupportedOperationException("문서화 전용 더미 로그인 API - LoginFilter가 가로챔");
    }

    @Operation(summary = "로그아웃", description = "Refresh Token(쿠키) 기준으로 서버 측 세션을 정리합니다. Access Token 인증 여부와 무관하게 permitAll입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그아웃 성공")
    })
    @PostMapping(value = "/logout")
    public ResultResponse<Void> logout() {
        throw new UnsupportedOperationException("문서화 전용 더미 로그아웃 API - LogoutFilter가 가로챔");
    }
}