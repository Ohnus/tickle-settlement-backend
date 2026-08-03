package com.settlement.tickle.domain.settlement.batch.tasklet;

import com.settlement.tickle.domain.settlement.batch.mapper.SettlementBatchMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.test.MetaDataInstanceFactory;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

// DailySettlementAggregationTasklet은 Spring이 관리하는 빈이 아니라(설정 클래스가 @StepScope로 직접
// new 해서 만듦) 생성자를 직접 호출해서 만든다. LocalDate 같은 값 타입까지 자동으로 채워주는 게 아니라서
// @InjectMocks는 안 쓴다 - 테스트마다 targetDate를 명시적으로 다르게 줄 수 있어야 하기도 하고.
@ExtendWith(MockitoExtension.class)
class DailySettlementAggregationTaskletTest {

    @Mock
    private SettlementBatchMapper settlementBatchMapper;

    // Tasklet이 StepContribution/ChunkContext를 실제로 쓰지 않지만, null을 넘기는 대신
    // spring-batch-test의 MetaDataInstanceFactory로 진짜와 같은 모양의 더미 객체를 만든다.
    private StepContribution stepContribution() {
        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        return stepExecution.createStepContribution();
    }

    private ChunkContext chunkContext() {
        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        return new ChunkContext(new StepContext(stepExecution));
    }

    @Nested
    @DisplayName("execute()는")
    class ExecuteTest {

        @Test
        @DisplayName("생성자로 받은 targetDate로 매퍼를 호출하고 FINISHED를 리턴한다.")
        void callsMapperWithTargetDate_andReturnsFinished() {

            // given
            LocalDate targetDate = LocalDate.of(2030, 6, 15);
            given(settlementBatchMapper.aggregateDailyNormal(targetDate)).willReturn(3);

            DailySettlementAggregationTasklet tasklet =
                    new DailySettlementAggregationTasklet(settlementBatchMapper, targetDate);

            // when
            RepeatStatus result = tasklet.execute(stepContribution(), chunkContext());

            // then
            assertThat(result).isEqualTo(RepeatStatus.FINISHED);
            verify(settlementBatchMapper).aggregateDailyNormal(targetDate);
        }

        @Test
        @DisplayName("합산 대상이 0건이어도 예외 없이 FINISHED를 리턴한다.")
        void returnsFinished_evenWhenNoGroupsInserted() {

            // given
            LocalDate targetDate = LocalDate.of(2030, 6, 16);
            given(settlementBatchMapper.aggregateDailyNormal(targetDate)).willReturn(0);

            DailySettlementAggregationTasklet tasklet =
                    new DailySettlementAggregationTasklet(settlementBatchMapper, targetDate);

            // when
            RepeatStatus result = tasklet.execute(stepContribution(), chunkContext());

            // then
            assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        }
    }
}
