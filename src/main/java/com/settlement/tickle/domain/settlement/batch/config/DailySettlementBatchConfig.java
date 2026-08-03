package com.settlement.tickle.domain.settlement.batch.config;

import com.settlement.tickle.domain.settlement.batch.mapper.SettlementBatchMapper;
import com.settlement.tickle.domain.settlement.batch.tasklet.DailySettlementAggregationTasklet;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;

// ### 동작 흐름
// - Job 실행 전 JobRepository 개입하여 Job 이름 + JobParameters 조합이 이미 성공한 적 있는지 확인.
// - 있으면 예외 던지고 거부, 없으면 BATCH_JOB_INSTANCE/BATCH_JOB_EXECUTION 테이블에 새 실행 기록 STARTING 기록.
// - Job이 Step 순서대로 실행(현재는 하나)하고 Step 시작 전에 BATCH_STEP_EXECUTION에 기록.
// - 이 시점에 @StepScope 빈 생성되고 Step이 시작되는 순간 jobParameters['targetDate'] 값이 채워짐.
// - LocalDate.parse(..)도 실행되며 Tasklet 객체 생성.
// - StepBuilder.tasklet(..)이 tasklet.excute() 호출하기 전에 transactionManager로 트랜잭션 열고 시작
// - tasklet.execute() 실행 -> targetDate 넘기며 매퍼 호출 -> 쿼리 실행 -> RepeatStatus.FINISHED 리턴 -> 트랜잭션 커밋.
// - JobRepository가 BATCH_STEP_EXECUTION을 COMPLETED로 업데이트, BATCH_JOB_EXECUTION도 COMPLETEED로 업데이트.
// - ( Tasklet 내부 반복 필요할 시 RepeatStatus.CONTINUABLE 리턴하여 excute() 다시 호출. )
@Configuration
@RequiredArgsConstructor
public class DailySettlementBatchConfig {

    private static final String JOB_NAME = "dailySettlementAggregationJob";
    private static final String STEP_NAME = "dailySettlementAggregationStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final SettlementBatchMapper settlementBatchMapper;

    @Bean
    public Job dailySettlementAggregationJob(Step dailySettlementAggregationStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(dailySettlementAggregationStep)
                .build();
    }

    @Bean
    public Step dailySettlementAggregationStep(Tasklet dailySettlementAggregationTasklet) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(dailySettlementAggregationTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet dailySettlementAggregationTasklet(
            @Value("#{jobParameters['targetDate']}") String targetDateParam) {
        return new DailySettlementAggregationTasklet(settlementBatchMapper, LocalDate.parse(targetDateParam));
    }
}
