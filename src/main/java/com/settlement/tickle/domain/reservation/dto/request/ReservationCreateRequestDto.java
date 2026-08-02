package com.settlement.tickle.domain.reservation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(description = "예매 생성 요청")
public class ReservationCreateRequestDto {

    @Schema(description = "예매할 공연 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "공연 ID를 입력하세요.")
    private final Long performanceId;
}
