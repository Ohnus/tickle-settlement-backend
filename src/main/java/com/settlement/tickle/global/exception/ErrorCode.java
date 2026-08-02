package com.settlement.tickle.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/* ==================
*  스프링 예외, 커스텀 예외 공통 코드
*  ================== */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    /* ==================
    *  Common(공통)
    *  ================== */
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "잘못된 입력 값입니다."),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "잘못된 타입 값입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 메서드입니다."),

    /* ==================
    *  Authentication(인증)
    *  ================== */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    INVALID_LOGIN_REQUEST(HttpStatus.BAD_REQUEST, "올바르지 않은 로그인 요청입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 존재하지 않습니다."),
    EXPIRED_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "Access Token이 만료되었습니다."),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh Token이 만료되었습니다."),

    /* ==================
    *  Authorization(인가)
    *  ================== */
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 부족합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    /* ==================
    *  OAuth2
    *  ================== */
    OAUTH2_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했습니다."),

    /* ==================
    *  User(회원)
    *  ================== */
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    MEMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 유저가 존재합니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    INVALID_SIGNUP_ROLE(HttpStatus.BAD_REQUEST, "허용되지 않은 회원 유형입니다."),
    HOST_BIZ_INFO_REQUIRED(HttpStatus.BAD_REQUEST, "판매자는 정산 지급 정보를 모두 입력해야 합니다."),

    /* ==================
    *  Performance(공연)
    *  ================== */
    PERFORMANCE_NOT_FOUND(HttpStatus.NOT_FOUND, "공연을 찾을 수 없습니다."),

    /* ==================
    *  Reservation(예매)
    *  ================== */
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예매를 찾을 수 없습니다."),
    RESERVATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 예매만 취소할 수 있습니다."),
    RESERVATION_ALREADY_CANCELED(HttpStatus.CONFLICT, "이미 취소된 예매입니다."),
    RESERVATION_CANCEL_PERIOD_EXPIRED(HttpStatus.BAD_REQUEST, "공연일 전날까지만 예매를 취소할 수 있습니다."),

    /* ==================
    *  Settlement(정산) — 예매 도메인에서 건별 정산과 연동할 때 사용
    *  ================== */
    SETTLEMENT_ENTRY_NOT_FOUND(HttpStatus.NOT_FOUND, "정산 항목을 찾을 수 없습니다."),

    /* ==================
    *  Status(공용 참조)
    *  ================== */
    STATUS_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "필요한 상태 코드가 시드되어 있지 않습니다.");

    private final HttpStatus status;
    private final String message;
}
