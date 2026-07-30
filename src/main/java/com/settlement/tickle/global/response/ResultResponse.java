package com.settlement.tickle.global.response;

import lombok.Getter;

@Getter
public class ResultResponse<T> {

    private final int status;
    private final String message;
    private final T data;

    public ResultResponse(ResultCode resultcode, T data) {
        this.status = resultcode.getStatus().value();
        this.message = resultcode.getMessage();
        this.data = data;
    }

    // 데이터 보내는 성공 응답
    public static <T> ResultResponse<T> of(ResultCode resultCode, T data) {
        return new ResultResponse<>(resultCode, data);
    }

    // 데이터 없는 성공 응답
    public static ResultResponse<Void> success(ResultCode resultCode) {
        return new ResultResponse<>(resultCode, null);
    }
}
