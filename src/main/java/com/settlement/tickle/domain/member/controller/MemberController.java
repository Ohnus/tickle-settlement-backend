package com.settlement.tickle.domain.member.controller;

import com.settlement.tickle.domain.member.dto.request.MemberExistsRequestDto;
import com.settlement.tickle.domain.member.dto.request.MemberSignupRequestDto;
import com.settlement.tickle.domain.member.dto.response.MemberInfoResponseDto;
import com.settlement.tickle.domain.member.service.MemberService;
import com.settlement.tickle.global.auth.custom.CustomUserPrincipal;
import com.settlement.tickle.global.exception.ErrorResponse;
import com.settlement.tickle.global.response.ResultCode;
import com.settlement.tickle.global.response.ResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Member", description = "회원 도메인 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberService memberService;

    // 이메일 중복 검사
    @Operation(summary = "이메일 중복 검사", description = "회원가입 전 이메일 사용 가능 여부를 확인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (중복 여부는 응답 데이터의 boolean 값)"),
            @ApiResponse(responseCode = "400", description = "이메일 형식이 올바르지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/email/exists")
    public ResultResponse<Boolean> existsEmailApi(@Valid @ParameterObject MemberExistsRequestDto existsRequestDto) {
        boolean exists = memberService.existsByEmail(existsRequestDto);
        return ResultResponse.of(ResultCode.MEMBER_EMAIL_CHECK_SUCCESS, exists);
    }

    // 닉네임 중복 검사
    @Operation(summary = "닉네임 중복 검사", description = "회원가입 전 닉네임 사용 가능 여부를 확인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (중복 여부는 응답 데이터의 boolean 값)"),
            @ApiResponse(responseCode = "400", description = "닉네임 형식이 올바르지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/nickname/exists")
    public ResultResponse<Boolean> existsNicknameApi(@Valid @ParameterObject MemberExistsRequestDto existsRequestDto) {
        boolean exists = memberService.existsByNickname(existsRequestDto);
        return ResultResponse.of(ResultCode.MEMBER_NICKNAME_CHECK_SUCCESS, exists);
    }

    // 회원가입
    @Operation(summary = "회원가입", description = "구매자(MEMBER) 또는 판매자(HOST)로 회원가입합니다. 관리자(ADMIN)는 공개 가입으로 생성할 수 없습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패 / 허용되지 않은 회원 유형(ADMIN) / 판매자 정산 지급 정보 누락",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 사용 중인 이메일 또는 닉네임",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResultResponse<?> signupApi(@Valid @RequestBody MemberSignupRequestDto signupRequestDto) {
        memberService.signup(signupRequestDto);
        return ResultResponse.success(ResultCode.MEMBER_CREATE_SUCCESS);
    }

    // 내 정보 조회 (판매자 대시보드 등에서 사용)
    @Operation(summary = "내 정보 조회", description = "로그인한 본인의 회원 정보를 조회합니다. 판매자 대시보드에서 사용자/정산 지급 정보 표시에 사용합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "회원을 찾을 수 없음(탈퇴 등)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "JWT")
    @GetMapping("/me")
    public ResultResponse<MemberInfoResponseDto> myInfoApi(@AuthenticationPrincipal CustomUserPrincipal principal) {
        MemberInfoResponseDto memberInfo = memberService.getMyInfo(principal.getUserId());
        return ResultResponse.of(ResultCode.MEMBER_INFO_SUCCESS, memberInfo);
    }

}