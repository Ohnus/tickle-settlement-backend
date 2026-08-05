package com.settlement.tickle.domain.settlement.batch.chunk.keyset;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

// 청크별 집계 내역을 Map에 담고 UPSERT로 최종 집계한다.
@RequiredArgsConstructor
public class KeysetSettlementDailyChunkWriter implements ItemWriter<KeysetSettlementEntryRow> {

    private static final String UPSERT_SQL = """
            INSERT INTO settlement_daily (member_id, performance_id, performance_title, settlement_date, entry_type,
                                           sales_amount, refund_amount, gross_amount, commission, net_amount,
                                           settlement_daily_created_at)
            VALUES (?, ?, ?, ?, 'NORMAL', ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (member_id, performance_id, settlement_date) WHERE entry_type = 'NORMAL'
            DO UPDATE SET
                sales_amount = settlement_daily.sales_amount + EXCLUDED.sales_amount,
                refund_amount = settlement_daily.refund_amount + EXCLUDED.refund_amount,
                gross_amount = settlement_daily.gross_amount + EXCLUDED.gross_amount,
                commission = settlement_daily.commission + EXCLUDED.commission,
                net_amount = settlement_daily.net_amount + EXCLUDED.net_amount
            """;

    private final JdbcTemplate jdbcTemplate;
    private final LocalDate targetDate;

    @Override
    public void write(Chunk<? extends KeysetSettlementEntryRow> chunk) {
        Map<GroupKey, Totals> grouped = new LinkedHashMap<>();
        for (KeysetSettlementEntryRow row : chunk) {
            grouped.computeIfAbsent(GroupKey.of(row), key -> new Totals()).add(row);
        }

        for (Map.Entry<GroupKey, Totals> entry : grouped.entrySet()) {
            GroupKey key = entry.getKey();
            Totals totals = entry.getValue();
            jdbcTemplate.update(UPSERT_SQL,
                    key.memberId(), key.performanceId(), key.performanceTitle(), targetDate,
                    totals.sales, totals.refund, totals.gross, totals.commission, totals.net);
        }
    }

    private record GroupKey(Long memberId, Long performanceId, String performanceTitle) {
        static GroupKey of(KeysetSettlementEntryRow row) {
            return new GroupKey(row.memberId(), row.performanceId(), row.performanceTitle());
        }
    }

    private static class Totals {
        private long sales;
        private long refund;
        private long gross;
        private long commission;
        private long net;

        void add(KeysetSettlementEntryRow row) {
            sales += row.salesAmount();
            refund += row.refundAmount();
            gross += row.grossAmount();
            commission += row.commission();
            net += row.netAmount();
        }
    }
}
