package com.settlement.tickle.domain.member.dto.response;

import com.settlement.tickle.domain.member.entity.Member;
import com.settlement.tickle.domain.member.entity.MemberRoleType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "내 정보 조회 응답 (판매자 대시보드 표시용)")
public record MemberInfoResponseDto(

        @Schema(description = "이메일", example = "user@example.com")
        String email,

        @Schema(description = "닉네임", example = "티클티클")
        String nickname,

        @Schema(description = "회원 유형 (MEMBER: 구매자, HOST: 판매자, ADMIN: 관리자)", example = "HOST")
        MemberRoleType role,

        @Schema(description = "가입일시")
        LocalDateTime createdAt,

        @Schema(description = "사업자등록번호 (판매자만 값이 있음)", example = "123-45-67890")
        String hostBizNumber,

        @Schema(description = "사업자명 (판매자만 값이 있음)", example = "티클컴퍼니")
        String hostBizName,

        @Schema(description = "정산 입금 은행 (판매자만 값이 있음)", example = "국민은행")
        String hostBizBank,

        @Schema(description = "정산 계좌 예금주 (판매자만 값이 있음)", example = "홍길동")
        String hostBizDepositor,

        @Schema(description = "정산 계좌번호 (판매자만 값이 있음)", example = "123456-78-901234")
        String hostBizBankNumber
) {
    public static MemberInfoResponseDto from(Member member) {
        return new MemberInfoResponseDto(
                member.getEmail(),
                member.getNickname(),
                member.getRole(),
                member.getCreatedAt(),
                member.getHostBizNumber(),
                member.getHostBizName(),
                member.getHostBizBank(),
                member.getHostBizDepositor(),
                member.getHostBizBankNumber()
        );
    }
}