package com.settlement.tickle.domain.settlement.batch.chunk.cursor;

public record SettlementEntryRow(
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
