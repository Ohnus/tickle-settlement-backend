package com.settlement.tickle.domain.settlement.repository;

import com.settlement.tickle.domain.member.entity.Member;
import com.settlement.tickle.domain.member.entity.MemberRoleType;
import com.settlement.tickle.domain.member.repository.MemberRepository;
import com.settlement.tickle.domain.performance.entity.Performance;
import com.settlement.tickle.domain.performance.repository.PerformanceRepository;
import com.settlement.tickle.domain.reservation.entity.Reservation;
import com.settlement.tickle.domain.reservation.repository.ReservationRepository;
import com.settlement.tickle.domain.settlement.entity.SettlementEntry;
import com.settlement.tickle.domain.status.entity.Status;
import com.settlement.tickle.domain.status.entity.StatusType;
import com.settlement.tickle.domain.status.repository.StatusRepository;
import com.settlement.tickle.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(JpaAuditingConfig.class)
@Testcontainers
class SettlementEntryRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6");

    @Autowired
    private SettlementEntryRepository settlementEntryRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private StatusRepository statusRepository;

    @Nested
    @DisplayName("findByReservation_Id()는")
    class FindByReservationIdTest {

        @Test
        @DisplayName("예매 ID로 연동된 건별 정산 항목을 조회한다.")
        void returnsSettlementEntry_whenReservationIdMatches() {

            // given
            Member host = memberRepository.save(Member.builder()
                    .email("repo-host@example.com").password("encoded").nickname("repoHost").role(MemberRoleType.HOST)
                    .build());
            Member buyer = memberRepository.save(Member.builder()
                    .email("repo-buyer@example.com").password("encoded").nickname("repoBuyer").role(MemberRoleType.MEMBER)
                    .build());

            Status performanceStatus = statusRepository.findByTypeAndDescription(StatusType.PERFORMANCE, "ON_SALE").orElseThrow();
            Performance performance = performanceRepository.save(Performance.builder()
                    .member(host).status(performanceStatus).title("리포지토리 테스트 공연").price(30000)
                    .startDate(LocalDateTime.now().plusDays(10)).endDate(LocalDateTime.now().plusDays(10).plusHours(2))
                    .reservationStartDate(LocalDateTime.now()).reservationEndDate(LocalDateTime.now().plusDays(9))
                    .build());

            Status reservedStatus = statusRepository.findByTypeAndDescription(StatusType.RESERVATION, "RESERVED").orElseThrow();
            Reservation reservation = reservationRepository.save(Reservation.builder()
                    .member(buyer).performance(performance).status(reservedStatus).code("RES-REPOTEST").price(30000)
                    .build());

            Status waitingStatus = statusRepository.findByTypeAndDescription(StatusType.SETTLEMENT, "WAITING").orElseThrow();
            settlementEntryRepository.save(SettlementEntry.builder()
                    .reservation(reservation).member(host).status(waitingStatus).performance(performance)
                    .performanceTitle(performance.getTitle()).performanceEndDate(performance.getEndDate())
                    .salesAmount(30000L).refundAmount(0L).grossAmount(30000L)
                    .contractCharge(new BigDecimal("0.050")).commission(1500L).netAmount(28500L)
                    .build());

            // when
            Optional<SettlementEntry> result = settlementEntryRepository.findByReservation_Id(reservation.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getSalesAmount()).isEqualTo(30000L);
        }

        @Test
        @DisplayName("연동된 정산 항목이 없으면 빈 Optional을 반환한다.")
        void returnsEmpty_whenNoSettlementEntryForReservation() {

            // when
            Optional<SettlementEntry> result = settlementEntryRepository.findByReservation_Id(999999L);

            // then
            assertThat(result).isEmpty();
        }
    }
}
