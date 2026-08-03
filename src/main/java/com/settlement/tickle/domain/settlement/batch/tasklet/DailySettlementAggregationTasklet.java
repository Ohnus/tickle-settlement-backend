package com.settlement.tickle.domain.settlement.batch.tasklet;

import com.settlement.tickle.domain.settlement.batch.mapper.SettlementBatchMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.time.LocalDate;

@RequiredArgsConstructor
@Slf4j
public class DailySettlementAggregationTasklet implements Tasklet {

    private final SettlementBatchMapper settlementBatchMapper;
    private final LocalDate targetDate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        int insertedGroupCount = settlementBatchMapper.aggregateDailyNormal(targetDate);
        log.info("일간 신규 집계 완료(WAITING -> NORMAL) - targetDate={}, insertedGroupCount={}",
                targetDate, insertedGroupCount);
        return RepeatStatus.FINISHED;
    }
}
