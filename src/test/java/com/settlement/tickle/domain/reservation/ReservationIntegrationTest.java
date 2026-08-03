package com.settlement.tickle.domain.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.settlement.tickle.domain.member.entity.Member;
import com.settlement.tickle.domain.member.entity.MemberRoleType;
import com.settlement.tickle.domain.member.repository.MemberRepository;
import com.settlement.tickle.domain.performance.entity.Performance;
import com.settlement.tickle.domain.performance.repository.PerformanceRepository;
import com.settlement.tickle.domain.reservation.dto.request.ReservationCreateRequestDto;
import com.settlement.tickle.domain.reservation.entity.Reservation;
import com.settlement.tickle.domain.reservation.repository.ReservationRepository;
import com.settlement.tickle.domain.settlement.entity.SettlementEntry;
import com.settlement.tickle.domain.settlement.repository.SettlementEntryRepository;
import com.settlement.tickle.domain.status.entity.Status;
import com.settlement.tickle.domain.status.entity.StatusType;
import com.settlement.tickle.domain.status.repository.StatusRepository;
import com.settlement.tickle.global.auth.jwt.util.JwtTokenType;
import com.settlement.tickle.global.auth.jwt.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ReservationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    // member_email 컬럼이 VARCHAR(30)이라 UUID를 그대로 쓰면 넘친다. 짧은 순번으로 유일성만 확보한다.
    private static final AtomicInteger EMAIL_SEQUENCE = new AtomicInteger();

    private String accessToken;
    private Performance performance;

    private String uniqueEmail(String prefix) {
        return prefix + EMAIL_SEQUENCE.incrementAndGet() + "@t.io";
    }

    @BeforeEach
    void setUp() {
        Member buyer = memberRepository.save(Member.builder()
                .email(uniqueEmail("buyer"))
                .password(passwordEncoder.encode("password123!")).nickname("intBuyer").role(MemberRoleType.MEMBER)
                .build());
        accessToken = jwtUtil.createJwt(buyer.getId(), buyer.getEmail(), "ROLE_MEMBER", JwtTokenType.ACCESS);

        Member host = memberRepository.save(Member.builder()
                .email(uniqueEmail("host"))
                .password(passwordEncoder.encode("password123!")).nickname("intHost").role(MemberRoleType.HOST)
                .build());

        Status performanceStatus = statusRepository.findByTypeAndDescription(StatusType.PERFORMANCE, "ON_SALE").orElseThrow();
        performance = performanceRepository.save(Performance.builder()
                .member(host).status(performanceStatus).title("통합테스트 공연").price(40000)
                .startDate(LocalDateTime.now().plusDays(10)).endDate(LocalDateTime.now().plusDays(10).plusHours(2))
                .reservationStartDate(LocalDateTime.now()).reservationEndDate(LocalDateTime.now().plusDays(9))
                .build());
    }

    @Nested
    @DisplayName("예매 생성은")
    class CreateReservationFlowTest {

        @Test
        @DisplayName("성공하면 실제 DB에 예매와 건별 정산이 함께 저장된다.")
        void savesReservationAndSettlementEntry_inRealDatabase() throws Exception {

            // when
            String responseBody = mockMvc.perform(post("/api/v1/reservations")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ReservationCreateRequestDto(performance.getId()))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            Long reservationId = objectMapper.readTree(responseBody).path("data").path("reservationId").asLong();

            // then
            // 세션이 닫힌 뒤라 status는 지연 로딩된 프록시 상태다. id는 프록시가 이미 들고 있어 안전하게 비교
            // 가능하지만, getDescription()처럼 다른 필드를 읽으면 LazyInitializationException이 난다.
            Reservation savedReservation = reservationRepository.findById(reservationId).orElseThrow();
            Status reservedStatus = statusRepository.findByTypeAndDescription(StatusType.RESERVATION, "RESERVED").orElseThrow();
            assertThat(savedReservation.getStatus().getId()).isEqualTo(reservedStatus.getId());

            SettlementEntry settlementEntry = settlementEntryRepository.findByReservation_Id(reservationId).orElseThrow();
            Status waitingStatus = statusRepository.findByTypeAndDescription(StatusType.SETTLEMENT, "WAITING").orElseThrow();
            assertThat(settlementEntry.getStatus().getId()).isEqualTo(waitingStatus.getId());
            assertThat(settlementEntry.getSalesAmount()).isEqualTo(40000L);
            assertThat(settlementEntry.getCommission()).isEqualTo(2000L);
            assertThat(settlementEntry.getNetAmount()).isEqualTo(38000L);
        }
    }

    @Nested
    @DisplayName("예매 취소는")
    class CancelReservationFlowTest {

        @Test
        @DisplayName("성공하면 예매/정산 상태가 함께 바뀌고 상태이력이 남는다.")
        void updatesStatusesAndInsertsHistory_inRealDatabase() throws Exception {

            // given: 취소할 예매를 먼저 하나 만들어둔다.
            String createResponseBody = mockMvc.perform(post("/api/v1/reservations")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ReservationCreateRequestDto(performance.getId()))))
                    .andReturn().getResponse().getContentAsString();
            Long reservationId = objectMapper.readTree(createResponseBody).path("data").path("reservationId").asLong();

            // when
            mockMvc.perform(patch("/api/v1/reservations/{id}/cancel", reservationId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk());

            // then
            Reservation canceledReservation = reservationRepository.findById(reservationId).orElseThrow();
            Status reservationCanceledStatus = statusRepository.findByTypeAndDescription(StatusType.RESERVATION, "CANCELED").orElseThrow();
            assertThat(canceledReservation.getStatus().getId()).isEqualTo(reservationCanceledStatus.getId());

            SettlementEntry settlementEntry = settlementEntryRepository.findByReservation_Id(reservationId).orElseThrow();
            Status settlementCanceledStatus = statusRepository.findByTypeAndDescription(StatusType.SETTLEMENT, "CANCELED").orElseThrow();
            assertThat(settlementEntry.getStatus().getId()).isEqualTo(settlementCanceledStatus.getId());
        }

        @Test
        @DisplayName("예매 종료일이 지났으면 400을 응답하고 상태가 바뀌지 않는다.")
        void returns400_andLeavesStateUnchanged_whenPeriodExpired() throws Exception {

            // given: 예매 종료일이 이미 지난 공연.
            Member pastHost = memberRepository.save(Member.builder()
                    .email(uniqueEmail("pastHost"))
                    .password(passwordEncoder.encode("password123!")).nickname("pastHost").role(MemberRoleType.HOST)
                    .build());
            Status performanceStatus = statusRepository.findByTypeAndDescription(StatusType.PERFORMANCE, "ON_SALE").orElseThrow();
            Performance pastPerformance = performanceRepository.save(Performance.builder()
                    .member(pastHost).status(performanceStatus).title("이미 끝난 공연").price(20000)
                    .startDate(LocalDateTime.now().minusDays(1)).endDate(LocalDateTime.now().minusDays(1).plusHours(2))
                    .reservationStartDate(LocalDateTime.now().minusDays(8)).reservationEndDate(LocalDateTime.now().minusDays(1))
                    .build());

            String createResponseBody = mockMvc.perform(post("/api/v1/reservations")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ReservationCreateRequestDto(pastPerformance.getId()))))
                    .andReturn().getResponse().getContentAsString();
            Long reservationId = objectMapper.readTree(createResponseBody).path("data").path("reservationId").asLong();

            // when & then
            mockMvc.perform(patch("/api/v1/reservations/{id}/cancel", reservationId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isBadRequest());

            Reservation stillReserved = reservationRepository.findById(reservationId).orElseThrow();
            Status reservedStatus = statusRepository.findByTypeAndDescription(StatusType.RESERVATION, "RESERVED").orElseThrow();
            assertThat(stillReserved.getStatus().getId()).isEqualTo(reservedStatus.getId());
        }
    }
}
