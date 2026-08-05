package com.settlement.tickle.domain.settlement.batch.chunk.cursor;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDate;

// 청크 커밋 간격(5000).
// Reader는 페이징이 아닌 커서(JdbcCursorItemReader) 방식. 최초 쿼리 1번으로 커서를 열어두고 필요할 때마다 스트리밍한다.
@Configuration
@RequiredArgsConstructor
public class DailySettlementAggregationChunkBaselineConfig {

    private static final String JOB_NAME = "dailySettlementAggregationChunkBaselineJob";
    private static final String STEP_NAME = "dailySettlementAggregationChunkBaselineStep";
    private static final int CHUNK_SIZE = 5000;

    private static final String SELECT_WAITING_ENTRIES_SQL = """
            SELECT se.member_id, se.performance_id, se.performance_title,
                   se.sales_amount, se.refund_amount, se.gross_amount, se.commission, se.net_amount
            FROM settlement_entry se
            JOIN status st ON st.status_id = se.status_id
            WHERE st.status_type = 'SETTLEMENT'
              AND st.status_description = 'WAITING'
              AND se.entry_created_at >= ?
              AND se.entry_created_at < ?
            ORDER BY se.settlement_entry_id
            """;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Bean
    public Job dailySettlementAggregationChunkBaselineJob(Step dailySettlementAggregationChunkBaselineStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(dailySettlementAggregationChunkBaselineStep)
                .build();
    }

    @Bean
    public Step dailySettlementAggregationChunkBaselineStep(
            ItemStreamReader<SettlementEntryRow> settlementEntryReader,
            ItemWriter<SettlementEntryRow> settlementDailyChunkWriter) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<SettlementEntryRow, SettlementEntryRow>chunk(CHUNK_SIZE, transactionManager)
                .reader(settlementEntryReader)
                .writer(settlementDailyChunkWriter)
                .build();
    }

    // 반환 타입을 ItemReader가 아니라 ItemStreamReader(= ItemReader + ItemStream)로 선언해야 한다.
    // @StepScope 프록시는 "이 @Bean 메서드의 선언된 반환 타입에 있는 인터페이스"만 구현해서 만들어지는데,
    // ItemReader는 open()/close() 생명주기를 가진 ItemStream을 상속하지 않는다.
    // 반환 타입을 ItemReader로 두면 프록시가 ItemStream을 구현하지 않게 되고, SimpleStepBuilder가 "이 reader는 스트림이 아니다"로
    // 판단해 open()을 호출해주지 않아 커서가 열리지 않은 채로 read()가 불려서 ReaderNotOpenException이 난다.
    // fetchSize를 명시하지 않으면 드라이버 기본값을 쓰는데, Postgres JDBC 드라이버는 autoCommit=true(기본값) 상태에서
    // fetchSize를 무시하고 결과를 통째로 한 번에 가져와 버리는 특이 동작이 있다.
    // 그래서 청크 크기와 맞춰 fetchSize를 명시적으로 지정한다(Spring Batch의 커서 리더가 이 값에 맞춰 커밋/커넥션을 관리해준다).
    // 커서는 open() 시점에 DataSource로부터 커넥션을 하나 받아서 계속 필드로 붙잡고 있다가, read()가 여러 번 호출되는 내내
    // 같은 커넥션 위에서 커서를 이어간다. 그러다 close()에서야 반납을 한다.
    // 따라서 직접 커넥션을 열고 붙잡고 관리해야 하므로 .dataSource(dataSource)로 원본 자체를 넘긴다.
    @Bean
    @StepScope
    public ItemStreamReader<SettlementEntryRow> settlementEntryReader(
            @Value("#{jobParameters['targetDate']}") String targetDateParam) {
        LocalDate targetDate = LocalDate.parse(targetDateParam);
        return new JdbcCursorItemReaderBuilder<SettlementEntryRow>()
                .name("settlementEntryReader")
                .dataSource(dataSource)
                .sql(SELECT_WAITING_ENTRIES_SQL)
                .fetchSize(CHUNK_SIZE)
                .preparedStatementSetter(ps -> {
                    ps.setObject(1, targetDate);
                    ps.setObject(2, targetDate.plusDays(1));
                })
                .rowMapper(new SettlementEntryRowMapper())
                .build();
    }

    @Bean
    @StepScope
    public ItemWriter<SettlementEntryRow> settlementDailyChunkWriter(
            @Value("#{jobParameters['targetDate']}") String targetDateParam) {
        return new SettlementDailyChunkWriter(jdbcTemplate, LocalDate.parse(targetDateParam));
    }
}
