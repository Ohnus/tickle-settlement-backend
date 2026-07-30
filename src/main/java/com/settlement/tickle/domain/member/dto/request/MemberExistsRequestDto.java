package com.settlement.tickle.domain.member.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 이메일/닉네임 중복 검사 공용 DTO — 호출하는 엔드포인트에 따라 둘 중 하나의 필드만 채워서 사용
@Getter
@RequiredArgsConstructor
@Schema(description = "이메일/닉네임 중복 검사 요청 — 호출하는 엔드포인트에 해당하는 필드만 채워서 사용")
public class MemberExistsRequestDto {

    @Schema(description = "중복 확인할 이메일 (이메일 중복 검사에서만 사용)", example = "user1@example.com")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 30, message = "이메일은 30자를 초과할 수 없습니다.")
    private final String email;

    @Schema(description = "중복 확인할 닉네임 (닉네임 중복 검사에서만 사용)", example = "티클티클")
    @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하로 입력하세요.")
    private final String nickname;
}