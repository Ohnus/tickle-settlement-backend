package com.settlement.tickle.domain.settlement.batch.chunk.keyset;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

// Reader 실행
// 스텝당 딱 한 번, 읽기 시작 전에 reader.open(executionContext) 호출.
// 최초 실행이면 컨텍스트가 비어 있어서 lastId가 0부터 시작. 재시작이면 이전에 저장해둔 값부터 이어감.
// 청크(5,000) 루프 반복 - read()가 최대 5,000번 호출되며 청크 채움.
// - currentPage(현재 페이지 반복자)가 비어 있으면 loadNextPage() 호출.
// - jdbcTemplate.query(..) 실행 -> RowMapper가 각 행을 DTO로 변환하여 최대 5,000건인 새 페이지를 currentPage에 세팅.
// - currentPage(최대 5,000)에 남은 게 있으면 한 row 꺼내서 DTO에 담고 리턴. 이 때, lastId 갱신.
// - 이 리턴되는 row가 5,000개 될 때 까지 read() 반복.
// - currentPage에 남은 게 없으면 loadPage() 다시 호출 및 위 작업 반복.
// - loadPage()에서도 더이상 채울 게 없으면 null 리턴 -> 청크 스텝이 끝난 것으로 인식.
// 5,000개 청크가 다 차면 writer.write(chunk) 실행 및 write에서 합산하여 UPSERT.
// 커밋 직전 reader.update(executionContext)
// - executionContext.putLong(LAST_ID_CONTEXT_KEY, lastId)로 지금까지 진행한 위치 체크포인트로 저장
// - 진행 중인 청크의 트랜잭션과 같이 커밋되므로 중간에 실패해도 이미 성공한 청크까지는 그 지점부터 재시작 가능
// 위 과정을 읽을 데이터가 다 떨어질 때까지 반복
// read()가 최종적으로 null 리턴하면 reader.close() 호출 및 Step, Job 완료
public class SettlementEntryKeysetItemReader implements ItemStreamReader<KeysetSettlementEntryRow> {

    private static final String LAST_ID_CONTEXT_KEY = "settlementEntryKeysetItemReader.lastId";

    // entry_created_at은 TIMESTAMPTZ라 = 로 비교하면 그날 자정 정각 한 순간만 매칭되고,
    // ::date로 캐스팅하면 의미는 맞지만 인덱스를 못 탄다(인덱스는 원본 값 기준이라 캐스팅된 값과 안 맞음).
    // 그래서 "그날 하루"를 범위 조건(>= 당일 00:00, < 다음날 00:00)으로 표현한다.
    private static final String SELECT_PAGE_SQL = """
            SELECT se.settlement_entry_id, se.member_id, se.performance_id, se.performance_title,
                   se.sales_amount, se.refund_amount, se.gross_amount, se.commission, se.net_amount
            FROM settlement_entry se
            JOIN status st ON st.status_id = se.status_id
            WHERE st.status_type = 'SETTLEMENT'
              AND st.status_description = 'WAITING'
              AND se.entry_created_at >= ?
              AND se.entry_created_at < ?
              AND se.settlement_entry_id > ?
            ORDER BY se.settlement_entry_id
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final LocalDate targetDate;
    private final LocalDate nextDate;
    private final int pageSize;
    private final KeysetSettlementEntryRowMapper rowMapper = new KeysetSettlementEntryRowMapper();

    private long lastId;
    private Iterator<KeysetSettlementEntryRow> currentPage = Collections.emptyIterator();

    public SettlementEntryKeysetItemReader(JdbcTemplate jdbcTemplate, LocalDate targetDate, int pageSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.targetDate = targetDate;
        this.nextDate = targetDate.plusDays(1);
        this.pageSize = pageSize;
    }

    @Override
    public KeysetSettlementEntryRow read() {
        if (!currentPage.hasNext()) {
            loadNextPage();
        }
        if (!currentPage.hasNext()) {
            return null; // 더 읽을 페이지가 없으면 null - 청크 스텝이 이걸 "끝"으로 인식한다.
        }
        KeysetSettlementEntryRow row = currentPage.next();
        lastId = row.settlementEntryId();
        return row;
    }

    private void loadNextPage() {
        List<KeysetSettlementEntryRow> page = jdbcTemplate.query(SELECT_PAGE_SQL, rowMapper, targetDate, nextDate, lastId, pageSize);
        currentPage = page.iterator();
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        this.lastId = executionContext.getLong(LAST_ID_CONTEXT_KEY, 0L);
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        executionContext.putLong(LAST_ID_CONTEXT_KEY, lastId);
    }

    @Override
    public void close() throws ItemStreamException {
        // JdbcTemplate은 호출마다 커넥션을 반납하므로 여기서 따로 닫을 리소스가 없다
        // (커서 버전은 커넥션을 계속 들고 있어야 해서 close()가 의미 있었던 것과 대비되는 지점).
    }
}
