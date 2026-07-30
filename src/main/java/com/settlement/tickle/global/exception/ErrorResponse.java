package com.settlement.tickle.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
public class ErrorResponse {

    private final int status;
    private final String message;
    private final List<FieldError> errors;

    private ErrorResponse(ErrorCode errorCode) {
        this.status = errorCode.getStatus().value();
        this.message = errorCode.getMessage();
        this.errors = List.of();
    }

    private ErrorResponse(ErrorCode errorCode, List<FieldError> errors) {
        this.status = errorCode.getStatus().value();
        this.message = errorCode.getMessage();
        this.errors = errors;
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode);
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> errors) {
        return new ErrorResponse(errorCode, errors);
    }

    // 어느 필드가 잘못됐는지 알림
    /* 예시) username = "", password = "12"
    *   @NotBlank(message="아이디를 입력하세요.")
    *   private String username;
    *   @Size(min=8, message="비밀번호는 8자 이상입니다.")
    *   private String password;
    *   {
            "status":400,
            "message":"잘못된 입력 값입니다.",
            "errors":[
                {
                    "field":"username",
                    "rejectedValue":"",
                    "reason":"아이디를 입력하세요."
                },
                {
                    "field":"password",
                    "rejectedValue":"12",
                    "reason":"비밀번호는 8자 이상입니다."
                }
            ]
        }
    * */
    @Getter
    @AllArgsConstructor
    public static class FieldError {

        private String field;
        private Object rejectedValue;
        private String reason;

        public static FieldError of(String field, Object rejectedValue, String reason) {
            return new FieldError(field, rejectedValue, reason);
        }
    }
}
