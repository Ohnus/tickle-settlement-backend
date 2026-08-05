package com.settlement.tickle.domain.settlement.batch.chunk.keyset;

// 키셋은 "다음 페이지를 어디서부터 읽을지"를 애플리케이션이 직접 알아야 하므로(id > :lastId),
// 그 커서로 쓸 id 자체가 row 데이터에 포함돼야 한다. 커서 방식은 서버가 위치를 기억해주니 이 필드가 필요 없다.
public record KeysetSettlementEntryRow(
        Long settlementEntryId,
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
