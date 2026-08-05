package com.settlement.tickle.domain.settlement.batch.chunk.keyset;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;

// 청크 크기, 페이지 크기 둘 다 5000으로 맞춘다.
// 크기가 다르면 "한 청크 안에 여러 페이지가 섞이거나 한 페이지가 여러 청크에 걸치는" 상황이 생겨서 비교 실험 조건이 지저분해진다.
@Configuration
@RequiredArgsConstructor
public class DailySettlementAggregationChunkKeysetConfig {

    private static final String JOB_NAME = "dailySettlementAggregationChunkKeysetJob";
    private static final String STEP_NAME = "dailySettlementAggregationChunkKeysetStep";
    private static final int CHUNK_SIZE = 5000;
    private static final int PAGE_SIZE = 5000;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JdbcTemplate jdbcTemplate;

    @Bean
    public Job dailySettlementAggregationChunkKeysetJob(Step dailySettlementAggregationChunkKeysetStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(dailySettlementAggregationChunkKeysetStep)
                .build();
    }

    @Bean
    public Step dailySettlementAggregationChunkKeysetStep(
            ItemStreamReader<KeysetSettlementEntryRow> settlementEntryKeysetReader,
            ItemWriter<KeysetSettlementEntryRow> settlementDailyChunkKeysetWriter) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<KeysetSettlementEntryRow, KeysetSettlementEntryRow>chunk(CHUNK_SIZE, transactionManager)
                .reader(settlementEntryKeysetReader)
                .writer(settlementDailyChunkKeysetWriter)
                .build();
    }

    // 커스텀 Reader는 생성자로 DataSource가 아니라 JdbcTemplate을 받는다.
    // JdbcTemplate은 이미 내부에 DataSource를 감싸고 있고, jdbcTemplate.query(..)를 호출할 때마다
    // 커넥션을 빌렸다가 끝나면 바로 반납하는 식으로 동작한다.
    // 커스텀 Reader의 loadNextPage()는 페이지마다 독립적인 쿼리를 날리는 구조여서 커넥션을 유지할 필요가 없다.
    @Bean
    @StepScope
    public ItemStreamReader<KeysetSettlementEntryRow> settlementEntryKeysetReader(
            @Value("#{jobParameters['targetDate']}") String targetDateParam) {
        return new SettlementEntryKeysetItemReader(jdbcTemplate, LocalDate.parse(targetDateParam), PAGE_SIZE);
    }

    @Bean
    @StepScope
    public ItemWriter<KeysetSettlementEntryRow> settlementDailyChunkKeysetWriter(
            @Value("#{jobParameters['targetDate']}") String targetDateParam) {
        return new KeysetSettlementDailyChunkWriter(jdbcTemplate, LocalDate.parse(targetDateParam));
    }
}
