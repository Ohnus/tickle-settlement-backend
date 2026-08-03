package com.settlement.tickle.domain.reservation.service;

import com.settlement.tickle.domain.member.entity.Member;
import com.settlement.tickle.domain.member.entity.MemberRoleType;
import com.settlement.tickle.domain.member.repository.MemberRepository;
import com.settlement.tickle.domain.performance.entity.Performance;
import com.settlement.tickle.domain.performance.repository.PerformanceRepository;
import com.settlement.tickle.domain.reservation.dto.request.ReservationCreateRequestDto;
import com.settlement.tickle.domain.reservation.dto.response.ReservationCreateResponseDto;
import com.settlement.tickle.domain.reservation.entity.Reservation;
import com.settlement.tickle.domain.reservation.repository.ReservationRepository;
import com.settlement.tickle.domain.settlement.entity.SettlementEntry;
import com.settlement.tickle.domain.settlement.entity.SettlementStatusHistory;
import com.settlement.tickle.domain.settlement.repository.SettlementEntryRepository;
import com.settlement.tickle.domain.settlement.repository.SettlementStatusHistoryRepository;
import com.settlement.tickle.domain.status.entity.Status;
import com.settlement.tickle.domain.status.entity.StatusType;
import com.settlement.tickle.domain.status.repository.StatusRepository;
import com.settlement.tickle.global.exception.BusinessException;
import com.settlement.tickle.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private PerformanceRepository performanceRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private StatusRepository statusRepository;

    @Mock
    private SettlementEntryRepository settlementEntryRepository;

    @Mock
    private SettlementStatusHistoryRepository settlementStatusHistoryRepository;

    @InjectMocks
    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        // contractChargeRate는 생성자 주입이 아니라 @Value 필드라서 @InjectMocks로는 채워지지 않는다.
        // Spring 컨텍스트 없이 값을 채우려면 ReflectionTestUtils로 직접 주입해야 한다.
        ReflectionTestUtils.setField(reservationService, "contractChargeRate", new BigDecimal("0.050"));
    }

    private Member buildBuyer(Long id) {
        Member buyer = Member.builder()
                .email("buyer@example.com").password("encoded").nickname("buyer").role(MemberRoleType.MEMBER)
                .build();
        ReflectionTestUtils.setField(buyer, "id", id);
        return buyer;
    }

    private Reservation buildReservation(Member buyer, Status reservationStatus, LocalDateTime reservationEndDate) {
        Member host = Member.builder()
                .email("host@example.com").password("encoded").nickname("host").role(MemberRoleType.HOST)
                .build();
        ReflectionTestUtils.setField(host, "id", 999L);

        Status performanceStatus = Status.builder().code(1).description("ON_SALE").type(StatusType.PERFORMANCE).build();
        Performance performance = Performance.builder()
                .member(host).status(performanceStatus).title("콘서트").price(50000)
                .startDate(reservationEndDate.plusDays(1)).endDate(reservationEndDate.plusDays(1).plusHours(3))
                .reservationStartDate(reservationEndDate.minusDays(7)).reservationEndDate(reservationEndDate)
                .build();

        Reservation reservation = Reservation.builder()
                .member(buyer).performance(performance).status(reservationStatus).code("RES-TEST0001").price(50000)
                .build();
        ReflectionTestUtils.setField(reservation, "id", 100L);
        return reservation;
    }

    @Nested
    @DisplayName("createReservation()은")
    class CreateReservationTest {

        @Test
        @DisplayName("회원과 공연이 존재하면 예매와 건별 정산을 함께 생성한다.")
        void createsReservationAndSettlementEntry_whenValid() {

            // given
            Member buyer = buildBuyer(1L);
            Member host = Member.builder()
                    .email("host@example.com").password("encoded").nickname("host").role(MemberRoleType.HOST)
                    .build();
            ReflectionTestUtils.setField(host, "id", 2L);

            Status performanceStatus = Status.builder().code(1).description("ON_SALE").type(StatusType.PERFORMANCE).build();
            Performance performance = Performance.builder()
                    .member(host).status(performanceStatus).title("콘서트").price(50000)
                    .startDate(LocalDateTime.now().plusDays(10)).endDate(LocalDateTime.now().plusDays(10).plusHours(3))
                    .reservationStartDate(LocalDateTime.now()).reservationEndDate(LocalDateTime.now().plusDays(9))
                    .build();
            ReflectionTestUtils.setField(performance, "id", 1L);

            Status reservedStatus = Status.builder().code(1).description("RESERVED").type(StatusType.RESERVATION).build();
            Status waitingStatus = Status.builder().code(1).description("WAITING").type(StatusType.SETTLEMENT).build();

            given(memberRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(buyer));
            given(performanceRepository.findById(1L)).willReturn(Optional.of(performance));
            given(statusRepository.findByTypeAndDescription(StatusType.RESERVATION, "RESERVED")).willReturn(Optional.of(reservedStatus));
            given(statusRepository.findByTypeAndDescription(StatusType.SETTLEMENT, "WAITING")).willReturn(Optional.of(waitingStatus));

            // when
            ReservationCreateResponseDto result = reservationService.createReservation(1L, new ReservationCreateRequestDto(1L));

            // then
            // reservationRepository가 Mock이라 save()가 실제로 id를 채워주지 않는다. 실제 DB라면 채워졌을 값.
            assertThat(result.reservationId()).isNull();
            assertThat(result.reservationCode()).startsWith("RES-");
            assertThat(result.performanceTitle()).isEqualTo("콘서트");
            assertThat(result.price()).isEqualTo(50000);
            assertThat(result.status()).isEqualTo("RESERVED");

            ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
            verify(reservationRepository).save(reservationCaptor.capture());
            assertThat(reservationCaptor.getValue().getMember()).isEqualTo(buyer);
            assertThat(reservationCaptor.getValue().getStatus()).isEqualTo(reservedStatus);

            ArgumentCaptor<SettlementEntry> entryCaptor = ArgumentCaptor.forClass(SettlementEntry.class);
            verify(settlementEntryRepository).save(entryCaptor.capture());
            SettlementEntry savedEntry = entryCaptor.getValue();
            assertThat(savedEntry.getMember()).isEqualTo(host);
            assertThat(savedEntry.getStatus()).isEqualTo(waitingStatus);
            assertThat(savedEntry.getSalesAmount()).isEqualTo(50000L);
            assertThat(savedEntry.getCommission()).isEqualTo(2500L);
            assertThat(savedEntry.getNetAmount()).isEqualTo(47500L);
            assertThat(savedEntry.getContractCharge()).isEqualByComparingTo("0.050");
        }

        @Test
        @DisplayName("존재하지 않는 회원이면 MEMBER_NOT_FOUND 예외를 던진다.")
        void throwsException_whenMemberNotFound() {

            // given
            given(memberRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

            // when
            BusinessException exception = catchThrowableOfType(
                    BusinessException.class,
                    () -> reservationService.createReservation(1L, new ReservationCreateRequestDto(1L))
            );

            // then
            assertThat(exception.getErrorcode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
            verify(reservationRepository, never()).save(any());
        }

        @Test
        @DisplayName("존재하지 않는 공연이면 PERFORMANCE_NOT_FOUND 예외를 던진다.")
        void throwsException_whenPerformanceNotFound() {

            // given
            given(memberRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(buildBuyer(1L)));
            given(performanceRepository.findById(1L)).willReturn(Optional.empty());

            // when
            BusinessException exception = catchThrowableOfType(
                    BusinessException.class,
                    () -> reservationService.createReservation(1L, new ReservationCreateRequestDto(1L))
            );

            // then
            assertThat(exception.getErrorcode()).isEqualTo(ErrorCode.PERFORMANCE_NOT_FOUND);
            verify(reservationRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("cancelReservation()은")
    class CancelReservationTest {

        private Status reservedStatus;
        private Status reservationCanceledStatus;
        private Status waitingStatus;
        private Status settlementCanceledStatus;

        @BeforeEach
        void setUpStatuses() {
            reservedStatus = Status.builder().code(1).description("RESERVED").type(StatusType.RESERVATION).build();
            reservationCanceledStatus = Status.builder().code(2).description("CANCELED").type(StatusType.RESERVATION).build();
            waitingStatus = Status.builder().code(1).description("WAITING").type(StatusType.SETTLEMENT).build();
            settlementCanceledStatus = Status.builder().code(2).description("CANCELED").type(StatusType.SETTLEMENT).build();
        }

        @Test
        @DisplayName("정상 취소하면 예매와 정산 상태를 함께 변경하고 이력을 남긴다.")
        void cancelsReservationAndSettlementEntry_whenValid() {

            // given
            Member buyer = buildBuyer(1L);
            Reservation reservation = buildReservation(buyer, reservedStatus, LocalDateTime.now().plusDays(10));

            SettlementEntry settlementEntry = SettlementEntry.builder()
                    .reservation(reservation).member(reservation.getPerformance().getMember())
                    .status(waitingStatus).performance(reservation.getPerformance())
                    .performanceTitle("콘서트").performanceEndDate(reservation.getPerformance().getEndDate())
                    .salesAmount(50000L).refundAmount(0L).grossAmount(50000L)
                    .contractCharge(new BigDecimal("0.050")).commission(2500L).netAmount(47500L)
                    .build();

            given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation));
            given(statusRepository.findByTypeAndDescription(StatusType.RESERVATION, "CANCELED")).willReturn(Optional.of(reservationCanceledStatus));
            given(settlementEntryRepository.findByReservation_Id(100L)).willReturn(Optional.of(settlementEntry));
            given(statusRepository.findByTypeAndDescription(StatusType.SETTLEMENT, "CANCELED")).willReturn(Optional.of(settlementCanceledStatus));

            // when
            reservationService.cancelReservation(1L, 100L);

            // then
            assertThat(reservation.getStatus()).isEqualTo(reservationCanceledStatus);
            assertThat(settlementEntry.getStatus()).isEqualTo(settlementCanceledStatus);

            ArgumentCaptor<SettlementStatusHistory> historyCaptor = ArgumentCaptor.forClass(SettlementStatusHistory.class);
            verify(settlementStatusHistoryRepository).save(historyCaptor.capture());
            SettlementStatusHistory history = historyCaptor.getValue();
            assertThat(history.getPreviousStatus()).isEqualTo(waitingStatus);
            assertThat(history.getChangedStatus()).isEqualTo(settlementCanceledStatus);
            assertThat(history.getChangeReason()).isEqualTo("예매 취소");
        }

        @Test
        @DisplayName("존재하지 않는 예매면 RESERVATION_NOT_FOUND 예외를 던진다.")
        void throwsException_whenReservationNotFound() {

            // given
            given(reservationRepository.findById(100L)).willReturn(Optional.empty());

            // when
            BusinessException exception = catchThrowableOfType(
                    BusinessException.class,
                    () -> reservationService.cancelReservation(1L, 100L)
            );

            // then
            assertThat(exception.getErrorcode()).isEqualTo(ErrorCode.RESERVATION_NOT_FOUND);
        }

        @Test
        @DisplayName("본인의 예매가 아니면 RESERVATION_ACCESS_DENIED 예외를 던진다.")
        void throwsException_whenNotOwner() {

            // given
            Member buyer = buildBuyer(1L);
            Reservation reservation = buildReservation(buyer, reservedStatus, LocalDateTime.now().plusDays(10));
            given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation));

            // when
            BusinessException exception = catchThrowableOfType(
                    BusinessException.class,
                    () -> reservationService.cancelReservation(999L, 100L)
            );

            // then
            assertThat(exception.getErrorcode()).isEqualTo(ErrorCode.RESERVATION_ACCESS_DENIED);
        }

        @Test
        @DisplayName("이미 취소된 예매면 RESERVATION_ALREADY_CANCELED 예외를 던진다.")
        void throwsException_whenAlreadyCanceled() {

            // given
            Member buyer = buildBuyer(1L);
            Reservation reservation = buildReservation(buyer, reservationCanceledStatus, LocalDateTime.now().plusDays(10));
            given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation));

            // when
            BusinessException exception = catchThrowableOfType(
                    BusinessException.class,
                    () -> reservationService.cancelReservation(1L, 100L)
            );

            // then
            assertThat(exception.getErrorcode()).isEqualTo(ErrorCode.RESERVATION_ALREADY_CANCELED);
        }

        @Test
        @DisplayName("예매 종료일을 지났으면 RESERVATION_CANCEL_PERIOD_EXPIRED 예외를 던진다.")
        void throwsException_whenCancelPeriodExpired() {

            // given: 예매 종료일이 이미 지난 상태.
            Member buyer = buildBuyer(1L);
            Reservation reservation = buildReservation(buyer, reservedStatus, LocalDateTime.now().minusDays(1));
            given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation));

            // when
            BusinessException exception = catchThrowableOfType(
                    BusinessException.class,
                    () -> reservationService.cancelReservation(1L, 100L)
            );

            // then
            assertThat(exception.getErrorcode()).isEqualTo(ErrorCode.RESERVATION_CANCEL_PERIOD_EXPIRED);
        }

        @Test
        @DisplayName("연동된 정산 항목이 없으면 SETTLEMENT_ENTRY_NOT_FOUND 예외를 던진다.")
        void throwsException_whenSettlementEntryNotFound() {

            // given
            Member buyer = buildBuyer(1L);
            Reservation reservation = buildReservation(buyer, reservedStatus, LocalDateTime.now().plusDays(10));
            given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation));
            given(statusRepository.findByTypeAndDescription(StatusType.RESERVATION, "CANCELED")).willReturn(Optional.of(reservationCanceledStatus));
            given(settlementEntryRepository.findByReservation_Id(100L)).willReturn(Optional.empty());

            // when
            BusinessException exception = catchThrowableOfType(
                    BusinessException.class,
                    () -> reservationService.cancelReservation(1L, 100L)
            );

            // then
            assertThat(exception.getErrorcode()).isEqualTo(ErrorCode.SETTLEMENT_ENTRY_NOT_FOUND);
        }
    }
}
