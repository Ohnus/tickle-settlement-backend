package com.settlement.tickle.domain.settlement.batch.chunk.cursor;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RequiredArgsConstructor
public class SettlementDailyChunkWriter implements ItemWriter<SettlementEntryRow> {

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
    public void write(Chunk<? extends SettlementEntryRow> chunk) {
        Map<GroupKey, Totals> grouped = new LinkedHashMap<>();
        for (SettlementEntryRow row : chunk) {
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
        static GroupKey of(SettlementEntryRow row) {
            return new GroupKey(row.memberId(), row.performanceId(), row.performanceTitle());
        }
    }

    private static class Totals {
        private long sales;
        private long refund;
        private long gross;
        private long commission;
        private long net;

        void add(SettlementEntryRow row) {
            sales += row.salesAmount();
            refund += row.refundAmount();
            gross += row.grossAmount();
            commission += row.commission();
            net += row.netAmount();
        }
    }
}
