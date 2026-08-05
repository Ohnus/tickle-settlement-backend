package com.settlement.tickle.domain.settlement.batch.chunk.offset;

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

// Offset 방식(Keyset, Cursor와 비교)
// 청크 크기, 페이지 크기 둘 다 5000으로 맞춘다.
@Configuration
@RequiredArgsConstructor
public class DailySettlementAggregationChunkOffsetConfig {

    private static final String JOB_NAME = "dailySettlementAggregationChunkOffsetJob";
    private static final String STEP_NAME = "dailySettlementAggregationChunkOffsetStep";
    private static final int CHUNK_SIZE = 5000;
    private static final int PAGE_SIZE = 5000;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JdbcTemplate jdbcTemplate;

    @Bean
    public Job dailySettlementAggregationChunkOffsetJob(Step dailySettlementAggregationChunkOffsetStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(dailySettlementAggregationChunkOffsetStep)
                .build();
    }

    @Bean
    public Step dailySettlementAggregationChunkOffsetStep(
            ItemStreamReader<OffsetSettlementEntryRow> settlementEntryOffsetReader,
            ItemWriter<OffsetSettlementEntryRow> settlementDailyChunkOffsetWriter) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<OffsetSettlementEntryRow, OffsetSettlementEntryRow>chunk(CHUNK_SIZE, transactionManager)
                .reader(settlementEntryOffsetReader)
                .writer(settlementDailyChunkOffsetWriter)
                .build();
    }

    @Bean
    @StepScope
    public ItemStreamReader<OffsetSettlementEntryRow> settlementEntryOffsetReader(
            @Value("#{jobParameters['targetDate']}") String targetDateParam) {
        return new SettlementEntryOffsetItemReader(jdbcTemplate, LocalDate.parse(targetDateParam), PAGE_SIZE);
    }

    @Bean
    @StepScope
    public ItemWriter<OffsetSettlementEntryRow> settlementDailyChunkOffsetWriter(
            @Value("#{jobParameters['targetDate']}") String targetDateParam) {
        return new OffsetSettlementDailyChunkWriter(jdbcTemplate, LocalDate.parse(targetDateParam));
    }
}
