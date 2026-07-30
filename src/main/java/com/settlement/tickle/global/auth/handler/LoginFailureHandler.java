package com.settlement.tickle.global.auth.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.settlement.tickle.global.exception.ErrorCode;
import com.settlement.tickle.global.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
@Qualifier("LoginFailureHandler")
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {

        ErrorCode errorCode;

        if(exception instanceof AuthenticationServiceException) {
            log.warn("로그인 실패(400) : {}", exception.getMessage(), exception);

            // POST 이외의 요청, JSON 깨짐 등 잘못된 요청 400 처리
            errorCode = ErrorCode.INVALID_LOGIN_REQUEST;
        } else {
            log.warn("로그인 실패(401) : {}", exception.getMessage(), exception);

            // 아이디, 비밀번호 불일치 등은 401 처리
            errorCode = ErrorCode.LOGIN_FAILED;
        }

        ErrorResponse responseBody = ErrorResponse.of(errorCode);

        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");

        objectMapper.writeValue(response.getWriter(), responseBody);
    }
}
