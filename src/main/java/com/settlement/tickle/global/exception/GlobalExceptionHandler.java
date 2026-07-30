package com.settlement.tickle.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/* ==================
 *  DispatcherServlet 통과 이후 발생하는 예외 처리
 *  비즈니스 로직에 사용되는 커스텀 예외 + 스프링 자체 예외
 *  ================== */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 커스텀 예외(보통 필드가 아닌 비즈니스 규칙 위반)
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {

        log.error("Business Exception: ", e);

        ErrorCode errorCode = e.getErrorcode();
        ErrorResponse response = ErrorResponse.of(errorCode);

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(response);
    }

    // 검증 예외(@RequestBody + @Valid + @NotBlank)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {

        log.error("Validation Exception: ", e);

        List<ErrorResponse.FieldError> errors = e.getBindingResult().getFieldErrors().stream().map(
                error -> ErrorResponse.FieldError.of(
                        error.getField(), error.getRejectedValue(), error.getDefaultMessage()
                )
        ).toList();

        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, errors);

        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(response);
    }

    // 검증 예외(타입)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<ErrorResponse> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {

        log.error("Type Mismatch Exception: ", e);

        List<ErrorResponse.FieldError> errors = List.of(
                ErrorResponse.FieldError.of(
                        e.getName(), e.getValue(), ErrorCode.INVALID_TYPE_VALUE.getMessage()
                )
        );

        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_TYPE_VALUE, errors);

        return ResponseEntity
                .status(ErrorCode.INVALID_TYPE_VALUE.getStatus())
                .body(response);
    }

    // 그 외
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception e) {

        log.error("Exception : ", e);

        ErrorResponse response = ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR);

        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(response);
    }

    // HTTP Method 검증
    /*@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        ErrorResponse response = ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }*/

    // DTO가 아닌 메서드 파라미터 검증(@RequestParam, @PathVariable, @RequestHanlder 등에 붙은 @NotBlank, @Min, @Positive)
    /*@ExceptionHandler(HandlerMethodValidationException.class)
    protected ResponseEntity<ErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }*/

    // Accept 헤더 검증
    /*@ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Object> handleNotAcceptable(HttpMediaTypeNotAcceptableException e) {
        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_TYPE_VALUE);
        return new ResponseEntity<>(response, HttpStatus.NOT_ACCEPTABLE);
    }*/

    // 폼데이터(@ModelAttribute) 예외
    /*@ExceptionHandler(BindException.class)
    protected ResponseEntity<ErrorResponse> handleBindException(BindException e) {
        final ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, e.getBindingResult());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }*/


}
