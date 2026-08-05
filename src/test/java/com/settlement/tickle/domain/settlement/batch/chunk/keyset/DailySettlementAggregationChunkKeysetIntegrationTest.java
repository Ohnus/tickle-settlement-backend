package com.settlement.tickle.domain.settlement.batch.chunk.keyset;

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
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
@Testcontainers
class DailySettlementAggregationChunkKeysetIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6");

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier("dailySettlementAggregationChunkKeysetJob")
    private Job chunkKeysetJob;

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

    private Member host;
    private Member buyer;
    private Performance performance;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(chunkKeysetJob);

        host = memberRepository.save(Member.builder()
                .email("keyset-host@t.io").password("encoded").nickname("keysetHost").role(MemberRoleType.HOST)
                .build());
        buyer = memberRepository.save(Member.builder()
                .email("keyset-buyer@t.io").password("encoded").nickname("ksetBuyer").role(MemberRoleType.MEMBER)
                .build());

        Status performanceStatus = statusRepository.findByTypeAndDescription(StatusType.PERFORMANCE, "ON_SALE").orElseThrow();
        performance = performanceRepository.save(Performance.builder()
                .member(host).status(performanceStatus).title("키셋 baseline 테스트 공연").price(10000)
                .startDate(LocalDateTime.now().plusDays(10)).endDate(LocalDateTime.now().plusDays(10).plusHours(2))
                .reservationStartDate(LocalDateTime.now()).reservationEndDate(LocalDateTime.now().plusDays(9))
                .build());
    }

    private void createSettlementEntry(long salesAmount, String settlementStatusDescription, LocalDateTime createdAt) {
        Status reservationStatus = statusRepository.findByTypeAndDescription(StatusType.RESERVATION, "RESERVED").orElseThrow();
        Reservation reservation = reservationRepository.save(Reservation.builder()
                .member(buyer).performance(performance).status(reservationStatus)
                .code("KEYSET-" + System.nanoTime()).price((int) salesAmount)
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

    @Test
    @DisplayName("Tasklet/커서 버전과 같은 시나리오에서 같은 결과를 낸다.")
    void aggregatesSameAsOtherVersions() throws Exception {

        // given
        LocalDate targetDate = LocalDate.of(2031, 3, 30);
        createSettlementEntry(10000L, "WAITING", targetDate.atTime(10, 0));
        createSettlementEntry(20000L, "WAITING", targetDate.atTime(11, 0));
        createSettlementEntry(99999L, "CANCELED", targetDate.atTime(12, 0));
        createSettlementEntry(88888L, "WAITING", targetDate.minusDays(1).atTime(10, 0));

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("targetDate", targetDate.toString())
                .toJobParameters());

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM settlement_daily WHERE member_id = ? AND performance_id = ?",
                host.getId(), performance.getId());
        assertThat(row.get("entry_type")).isEqualTo("NORMAL");
        assertThat(((Number) row.get("sales_amount")).longValue()).isEqualTo(30000L);
    }
}
