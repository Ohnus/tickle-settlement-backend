package com.settlement.tickle.domain.settlement.batch;

import com.settlement.tickle.domain.member.entity.Member;
import com.settlement.tickle.domain.member.entity.MemberRoleType;
import com.settlement.tickle.domain.member.repository.MemberRepository;
import com.settlement.tickle.domain.performance.entity.Performance;
import com.settlement.tickle.domain.performance.repository.PerformanceRepository;
import com.settlement.tickle.domain.reservation.entity.Reservation;
import com.settlement.tickle.domain.reservation.repository.ReservationRepository;
import com.settlement.tickle.domain.settlement.entity.SettlementEntry;
import com.settlement.tickle.domain.settlement.repository.SettlementEntryRepository;
import com.settlement.tickle.domain.status.entity.Status;
import com.settlement.tickle.domain.status.entity.StatusType;
import com.settlement.tickle.domain.status.repository.StatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Job/Step/@StepScope Tasklet/Mapper/DB까지 전체 배선이 실제로 맞물려 도는지 확인하는 레이어.
// - 매퍼 테스트(SettlementBatchMapperTest)가 "SQL이 맞는가"를 증명한다면, 여기는 "Job을 실행하면
//   그 SQL까지 실제로 도달하는가"를 증명한다.
// - spring.batch.job.enabled=false는 기동 시 자동 실행만 막을 뿐 JobLauncherTestUtils로 수동
//   실행하는 건 막지 않는다.
@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
@Testcontainers
class DailySettlementBatchIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6");

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    // 컨텍스트에 Job 빈이 두 개(Tasklet 버전 + 청크 baseline 버전)가 되면서 @SpringBatchTest의 자동
    // 단일 Job 감지가 더 이상 안 먹힌다(모호해서 그냥 null로 남는다) - 그래서 Tasklet 버전 Job을
    // 명시적으로 지정해준다.
    @Autowired
    @Qualifier("dailySettlementAggregationJob")
    private Job dailySettlementAggregationJob;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private SettlementEntryRepository settlementEntryRepository;

    @Autowired
    private StatusRepository statusRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // @SpringBootTest는 @DataJpaTest와 달리 테스트 간 자동 롤백이 없어서, 이메일뿐 아니라 targetDate도
    // 테스트마다 다르게 써야 한다 - 같은 날짜를 재사용하면 JobRepository가 "이미 완료된 JobInstance"로
    // 보고 뒤에 도는 테스트까지 실패시킨다.
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private Member host;
    private Member buyer;
    private Performance performance;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(dailySettlementAggregationJob);

        int seq = SEQUENCE.incrementAndGet();
        host = memberRepository.save(Member.builder()
                .email("batch-host" + seq + "@t.io").password("encoded").nickname("batchHost").role(MemberRoleType.HOST)
                .build());
        buyer = memberRepository.save(Member.builder()
                .email("batch-buyer" + seq + "@t.io").password("encoded").nickname("batchBuyer").role(MemberRoleType.MEMBER)
                .build());

        Status performanceStatus = statusRepository.findByTypeAndDescription(StatusType.PERFORMANCE, "ON_SALE").orElseThrow();
        performance = performanceRepository.save(Performance.builder()
                .member(host).status(performanceStatus).title("배치 통합테스트 공연 " + seq).price(10000)
                .startDate(LocalDateTime.now().plusDays(10)).endDate(LocalDateTime.now().plusDays(10).plusHours(2))
                .reservationStartDate(LocalDateTime.now()).reservationEndDate(LocalDateTime.now().plusDays(9))
                .build());
    }

    // JPA Auditing이 entry_created_at을 저장 시점(now)으로 강제하고 updatable=false라 엔티티를 통한
    // 수정도 반영되지 않는다. 그래서 저장 후 JdbcTemplate으로 직접 날짜를 덮어써서 "그 날짜에 생성된
    // 건"이라는 시나리오를 만든다 - 매퍼 테스트에서 JdbcTemplate으로 픽스처를 넣은 것과 같은 이유.
    private void createSettlementEntry(long salesAmount, String settlementStatusDescription, LocalDateTime createdAt) {
        Status reservationStatus = statusRepository.findByTypeAndDescription(StatusType.RESERVATION, "RESERVED").orElseThrow();
        Reservation reservation = reservationRepository.save(Reservation.builder()
                .member(buyer).performance(performance).status(reservationStatus)
                .code("BATCH-" + SEQUENCE.incrementAndGet()).price((int) salesAmount)
                .build());

        Status settlementStatus = statusRepository.findByTypeAndDescription(StatusType.SETTLEMENT, settlementStatusDescription).orElseThrow();
        long commission = Math.round(salesAmount * 0.05);
        SettlementEntry entry = settlementEntryRepository.save(SettlementEntry.builder()
                .reservation(reservation).member(host).status(settlementStatus).performance(performance)
                .performanceTitle(performance.getTitle()).performanceEndDate(performance.getEndDate())
                .salesAmount(salesAmount).refundAmount(0L).grossAmount(salesAmount)
                .contractCharge(new BigDecimal("0.050")).commission(commission).netAmount(salesAmount - commission)
                .build());

        jdbcTemplate.update("UPDATE settlement_entry SET entry_created_at = ?, entry_updated_at = ? WHERE settlement_entry_id = ?",
                createdAt, createdAt, entry.getId());
    }

    private JobParameters jobParameters(LocalDate targetDate) {
        return new JobParametersBuilder()
                .addString("targetDate", targetDate.toString())
                .toJobParameters();
    }

    @Nested
    @DisplayName("dailySettlementAggregationJob은")
    class RunJobTest {

        @Test
        @DisplayName("대상 날짜의 WAITING 건만 합산해 COMPLETED로 끝나고 settlement_daily에 반영된다.")
        void completesAndAggregatesIntoSettlementDaily() throws Exception {

            // given
            LocalDate targetDate = LocalDate.of(2031, 3, 10);
            createSettlementEntry(10000L, "WAITING", targetDate.atTime(10, 0));
            createSettlementEntry(20000L, "WAITING", targetDate.atTime(11, 0));
            createSettlementEntry(99999L, "CANCELED", targetDate.atTime(12, 0));
            createSettlementEntry(88888L, "WAITING", targetDate.minusDays(1).atTime(10, 0));

            // when
            JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters(targetDate));

            // then
            assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT * FROM settlement_daily WHERE member_id = ? AND performance_id = ?",
                    host.getId(), performance.getId());
            assertThat(row.get("entry_type")).isEqualTo("NORMAL");
            assertThat(((Number) row.get("sales_amount")).longValue()).isEqualTo(30000L);
        }

        @Test
        @DisplayName("같은 targetDate로 두 번 실행하면 두 번째 실행은 거부된다.")
        void rejectsSecondRun_withSameTargetDate() throws Exception {

            // given: 같은 날짜로 이미 한 번 완료된 실행이 있는 상태.
            LocalDate targetDate = LocalDate.of(2031, 3, 11);
            createSettlementEntry(10000L, "WAITING", targetDate.atTime(10, 0));
            jobLauncherTestUtils.launchJob(jobParameters(targetDate));

            // when & then: JobRepository가 "Job 이름 + JobParameters" 조합의 중복 실행을 막는다
            // (settlement_daily의 유니크 인덱스가 걸리기도 전에, 프레임워크 레벨에서 먼저 거부됨).
            assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(jobParameters(targetDate)))
                    .isInstanceOf(JobInstanceAlreadyCompleteException.class);
        }
    }
}
