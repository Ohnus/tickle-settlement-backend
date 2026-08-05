package com.settlement.tickle.global.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

// 정산 배치 4개 버전(오프셋/키셋/커서/Tasklet)을 실제 DB에 수동으로 테스트 실행해보기 위한 트리거
// manual.batch.trigger=true일 때만 동작한다(기본은 비활성).
//
// 실행 예: 오프셋/키셋/커서/Tasklet 순서(target-date 실행 기준으로 수정 필요)
// .\gradlew.bat bootRun --args="--manual.batch.trigger=true --manual.batch.job-name=dailySettlementAggregationChunkOffsetJob --manual.batch.target-date=2026-08-03"
// .\gradlew.bat bootRun --args="--manual.batch.trigger=true --manual.batch.job-name=dailySettlementAggregationChunkKeysetJob --manual.batch.target-date=2026-08-03"
// .\gradlew.bat bootRun --args="--manual.batch.trigger=true --manual.batch.job-name=dailySettlementAggregationChunkBaselineJob --manual.batch.target-date=2026-08-03"
// .\gradlew.bat bootRun --args="--manual.batch.trigger=true --manual.batch.job-name=dailySettlementAggregationJob --manual.batch.target-date=2026-08-03"

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "manual.batch.trigger", havingValue = "true")
public class ManualBatchTriggerRunner implements CommandLineRunner {

    private final JobLauncher jobLauncher;
    private final ApplicationContext applicationContext;

    @Value("${manual.batch.job-name}")
    private String jobName;

    @Value("${manual.batch.target-date}")
    private String targetDate;

    @Override
    public void run(String... args) throws Exception {
        Job job = applicationContext.getBean(jobName, Job.class);

        JobExecution jobExecution = jobLauncher.run(job, new JobParametersBuilder()
                .addString("targetDate", targetDate)
                .toJobParameters());

        log.info("수동 배치 실행 완료 - job={}, targetDate={}, status={}, exitStatus={}",
                jobName, targetDate, jobExecution.getStatus(), jobExecution.getExitStatus());
    }
}
