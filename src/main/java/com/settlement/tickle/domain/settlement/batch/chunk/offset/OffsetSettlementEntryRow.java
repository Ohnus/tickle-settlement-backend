package com.settlement.tickle.domain.settlement.batch.chunk.offset;

public record OffsetSettlementEntryRow(
        Long memberId,
        Long performanceId,
        String performanceTitle,
        long salesAmount,
        long refundAmount,
        long grossAmount,
        long commission,
        long netAmount
) {
}
