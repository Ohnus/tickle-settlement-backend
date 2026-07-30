package com.settlement.tickle.global.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ResultCode {

    /* ==================
    *  AUTH
    *  ================== */
    TOKEN_REISSUE_SUCCESS(HttpStatus.OK, "토큰 재발급이 완료되었습니다."),

    /* ==================
    *  MEMBER(회원)
    *  ================== */
    MEMBER_EMAIL_CHECK_SUCCESS(HttpStatus.OK, "이메일 중복 확인이 완료되었습니다."),
    MEMBER_NICKNAME_CHECK_SUCCESS(HttpStatus.OK, "닉네임 중복 확인이 완료되었습니다."),
    MEMBER_CREATE_SUCCESS(HttpStatus.CREATED, "회원가입이 완료되었습니다."),
    MEMBER_LOGIN_SUCCESS(HttpStatus.OK, "로그인 되었습니다."),
    MEMBER_LOGOUT_SUCCESS(HttpStatus.OK, "로그아웃 되었습니다."),
    MEMBER_INFO_SUCCESS(HttpStatus.OK, "유저 정보 조회 성공"),
    MEMBER_INFO_UPDATE_SUCCESS(HttpStatus.OK, "회원 정보가 수정되었습니다."),
    MEMBER_PASSWORD_CHANGE_SUCCESS(HttpStatus.OK, "비밀번호가 변경되었습니다."),
    MEMBER_DELETE_SUCCESS(HttpStatus.OK, "회원 탈퇴가 완료되었습니다.");

    private final HttpStatus status;
    private final String message;
}
