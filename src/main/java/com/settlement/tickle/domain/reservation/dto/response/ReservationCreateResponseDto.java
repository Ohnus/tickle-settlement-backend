package com.settlement.tickle.domain.reservation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "예매 생성 응답")
public record ReservationCreateResponseDto(

        @Schema(description = "예매 ID", example = "1")
        Long reservationId,

        @Schema(description = "예매 코드", example = "RES-A1B2C3D4")
        String reservationCode,

        @Schema(description = "공연명", example = "봄맞이 콘서트")
        String performanceTitle,

        @Schema(description = "예매 가격", example = "50000")
        Integer price,

        @Schema(description = "예매 상태", example = "RESERVED")
        String status
) {
}
