package com.settlement.tickle.domain.member.dto.request;

import com.settlement.tickle.domain.member.entity.MemberRoleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(description = "회원가입 요청")
public class MemberSignupRequestDto {

    @Schema(description = "이메일", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "이메일을 입력하세요.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 30, message = "이메일은 30자를 초과할 수 없습니다.")
    private final String email;

    @Schema(description = "비밀번호 (8~20자)", example = "password123!", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "비밀번호를 입력하세요.")
    @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하로 입력하세요.")
    private final String password;

    @Schema(description = "닉네임 (2~10자)", example = "티클티클", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "닉네임을 입력하세요.")
    @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하로 입력하세요.")
    private final String nickname;

    // 공개 회원가입은 구매자(MEMBER) 또는 판매자(HOST)만 선택 가능 — ADMIN은 서비스단에서 차단
    @Schema(description = "회원 유형 (MEMBER: 구매자, HOST: 판매자 — ADMIN은 공개 가입으로 선택 불가)",
            example = "MEMBER", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "회원 유형을 선택하세요.")
    private final MemberRoleType role;

    // role이 HOST(판매자)일 때만 필수인 정산 지급 정보
    @Schema(description = "사업자등록번호 (판매자만 필수)", example = "123-45-67890")
    private final String hostBizNumber;

    @Schema(description = "사업자명 (판매자만 필수)", example = "티클컴퍼니")
    private final String hostBizName;

    @Schema(description = "정산 입금 은행 (판매자만 필수)", example = "국민은행")
    private final String hostBizBank;

    @Schema(description = "정산 계좌 예금주 (판매자만 필수)", example = "홍길동")
    private final String hostBizDepositor;

    @Schema(description = "정산 계좌번호 (판매자만 필수)", example = "123456-78-901234")
    private final String hostBizBankNumber;
}