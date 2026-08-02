package com.settlement.tickle.domain.reservation.controller;

import com.settlement.tickle.domain.reservation.dto.request.ReservationCreateRequestDto;
import com.settlement.tickle.domain.reservation.dto.response.ReservationCreateResponseDto;
import com.settlement.tickle.domain.reservation.service.ReservationService;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Reservation", description = "예매 생성/취소 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    // 예매 생성 (건별 정산도 같은 트랜잭션에서 함께 생성됨)
    @Operation(summary = "예매 생성", description = "선택한 공연에 대해 예매를 생성합니다. 건별 정산 항목(WAITING)도 같은 트랜잭션에서 1:1로 함께 생성됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "예매 생성 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "회원 또는 공연을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "JWT")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ResultResponse<ReservationCreateResponseDto> createReservation(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody ReservationCreateRequestDto requestDto) {
        ReservationCreateResponseDto response = reservationService.createReservation(principal.getUserId(), requestDto);
        return ResultResponse.of(ResultCode.RESERVATION_CREATE_SUCCESS, response);
    }

    // 예매 취소 (건별 정산 상태 동기화 + 상태이력 INSERT도 같은 트랜잭션에서 함께 처리됨)
    @Operation(summary = "예매 취소", description = "본인의 예매를 취소합니다. 공연일 전날까지만 가능하며, 건별 정산 상태(WAITING → CANCELED) 업데이트와 상태이력 기록도 같은 트랜잭션에서 함께 처리됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "예매 취소 성공"),
            @ApiResponse(responseCode = "400", description = "취소 가능 기간(공연일 전날) 경과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "본인의 예매가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "예매를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 취소된 예매",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "JWT")
    @PatchMapping("/{reservationId}/cancel")
    public ResultResponse<Void> cancelReservation(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long reservationId) {
        reservationService.cancelReservation(principal.getUserId(), reservationId);
        return ResultResponse.success(ResultCode.RESERVATION_CANCEL_SUCCESS);
    }
}
