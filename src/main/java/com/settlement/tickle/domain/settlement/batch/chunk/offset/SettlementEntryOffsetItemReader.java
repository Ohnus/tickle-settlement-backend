package com.settlement.tickle.domain.settlement.batch.chunk.offset;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

// lastId, fetchSize 대신 OFFSET으로 다음 페이지를 정한다.
// Offset은 매 페이지 실제로 읽은 행 수만큼 증가시킨다(고정 pageSize만큼이 아니라).
// 마지막 페이지처럼 pageSize보다 적게 나온 경우에도 offset이 정확히 맞아야
// 그다음 read()가 빈 페이지를 받고 정상 종료(null 리턴)할 수 있다.
//
// Offset은 "몇 번째 행부터"를 인덱스로 한 번에 점프하지 못하고 그 앞의 행들을 하나하나 건너뛰며 세야 한다.
// 그래서 페이지가 뒤로 갈수록(offset이 커질수록) 건너뛰는 비용이 누적된다. 이게 키셋/커서와 비교 지점이다.
public class SettlementEntryOffsetItemReader implements ItemStreamReader<OffsetSettlementEntryRow> {

    private static final String OFFSET_CONTEXT_KEY = "settlementEntryOffsetItemReader.offset";

    private static final String SELECT_PAGE_SQL = """
            SELECT se.member_id, se.performance_id, se.performance_title,
                   se.sales_amount, se.refund_amount, se.gross_amount, se.commission, se.net_amount
            FROM settlement_entry se
            JOIN status st ON st.status_id = se.status_id
            WHERE st.status_type = 'SETTLEMENT'
              AND st.status_description = 'WAITING'
              AND se.entry_created_at >= ?
              AND se.entry_created_at < ?
            ORDER BY se.settlement_entry_id
            OFFSET ?
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final LocalDate targetDate;
    private final LocalDate nextDate;
    private final int pageSize;
    private final OffsetSettlementEntryRowMapper rowMapper = new OffsetSettlementEntryRowMapper();

    private long offset;
    private Iterator<OffsetSettlementEntryRow> currentPage = Collections.emptyIterator();

    public SettlementEntryOffsetItemReader(JdbcTemplate jdbcTemplate, LocalDate targetDate, int pageSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.targetDate = targetDate;
        this.nextDate = targetDate.plusDays(1);
        this.pageSize = pageSize;
    }

    @Override
    public OffsetSettlementEntryRow read() {
        if (!currentPage.hasNext()) {
            loadNextPage();
        }
        if (!currentPage.hasNext()) {
            return null; // 더 읽을 페이지가 없으면 null - 청크 스텝이 이걸 "끝"으로 인식한다.
        }
        return currentPage.next();
    }

    private void loadNextPage() {
        List<OffsetSettlementEntryRow> page = jdbcTemplate.query(SELECT_PAGE_SQL, rowMapper, targetDate, nextDate, offset, pageSize);
        offset += page.size();
        currentPage = page.iterator();
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        this.offset = executionContext.getLong(OFFSET_CONTEXT_KEY, 0L);
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        executionContext.putLong(OFFSET_CONTEXT_KEY, offset);
    }

    @Override
    public void close() throws ItemStreamException {
        // JdbcTemplate은 호출마다 커넥션을 반납하므로 여기서 따로 닫을 리소스가 없다.
    }
}
