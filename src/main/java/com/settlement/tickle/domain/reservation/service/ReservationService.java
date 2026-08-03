package com.settlement.tickle.domain.reservation.service;

import com.settlement.tickle.domain.member.entity.Member;
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    // Status는 자바 enum이 아니라 DB 시드 데이터라, 상태값을 (type, description) 문자열 조합으로 조회한다.
    // status_id를 하드코딩하지 않는 이유는 저장 순서가 바뀌면 값이 달라질 수 있기 때문이다.
    private static final String RESERVED = "RESERVED";
    private static final String CANCELED = "CANCELED";

    private final ReservationRepository reservationRepository;
    private final PerformanceRepository performanceRepository;
    private final MemberRepository memberRepository;
    private final StatusRepository statusRepository;
    private final SettlementEntryRepository settlementEntryRepository;
    private final SettlementStatusHistoryRepository settlementStatusHistoryRepository;

    // 전 판매자 공통 고정 수수료율. 정산 항목 생성 시점에 이 값을 스냅샷으로 저장해서,
    // 나중에 이 설정값이 바뀌어도 과거 정산 건은 판매 당시 요율을 그대로 유지한다.
    @Value("${host.contract.charge-rate}")
    private BigDecimal contractChargeRate;

    // 예매 생성: 예매(RESERVED) 생성과 건별 정산(SettlementEntry, WAITING)을 같은 트랜잭션에서 1:1로 함께 생성한다.
    @Transactional
    public ReservationCreateResponseDto createReservation(Long memberId, ReservationCreateRequestDto requestDto) {

        Member buyer = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Performance performance = performanceRepository.findById(requestDto.getPerformanceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND));

        Status reservedStatus = findStatus(StatusType.RESERVATION, RESERVED);
        Status waitingStatus = findStatus(StatusType.SETTLEMENT, "WAITING");

        Reservation reservation = Reservation.builder()
                .member(buyer)
                .performance(performance)
                .status(reservedStatus)
                .code(generateReservationCode())
                .price(performance.getPrice())
                .build();
        reservationRepository.save(reservation);

        // 정산 대상은 예매한 구매자가 아니라 공연을 등록한 판매자(호스트)다.
        long salesAmount = performance.getPrice();
        long refundAmount = 0L;
        long grossAmount = salesAmount - refundAmount;
        long commission = calculateCommission(grossAmount);
        long netAmount = grossAmount - commission;

        SettlementEntry settlementEntry = SettlementEntry.builder()
                .reservation(reservation)
                .member(performance.getMember())
                .status(waitingStatus)
                .performance(performance)
                .performanceTitle(performance.getTitle())
                .performanceEndDate(performance.getEndDate())
                .salesAmount(salesAmount)
                .refundAmount(refundAmount)
                .grossAmount(grossAmount)
                .contractCharge(contractChargeRate)
                .commission(commission)
                .netAmount(netAmount)
                .build();
        settlementEntryRepository.save(settlementEntry);

        return new ReservationCreateResponseDto(
                reservation.getId(),
                reservation.getCode(),
                performance.getTitle(),
                reservation.getPrice(),
                reservedStatus.getDescription()
        );
    }

    // 예매 취소: 공연일 전날까지만 가능. 예매 상태(RESERVED -> CANCELED) 변경과 건별 정산 상태
    // (WAITING -> CANCELED) 업데이트 + 상태이력 INSERT를 같은 트랜잭션에서 수행한다.
    @Transactional
    public void cancelReservation(Long memberId, Long reservationId) {

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.getMember().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.RESERVATION_ACCESS_DENIED);
        }

        if (CANCELED.equals(reservation.getStatus().getDescription())) {
            throw new BusinessException(ErrorCode.RESERVATION_ALREADY_CANCELED);
        }

        // 취소 가능 기간: 예매 종료일까지.
        Performance performance = reservation.getPerformance();
        if (LocalDateTime.now().isAfter(performance.getReservationEndDate())) {
            throw new BusinessException(ErrorCode.RESERVATION_CANCEL_PERIOD_EXPIRED);
        }

        Status reservationCanceledStatus = findStatus(StatusType.RESERVATION, CANCELED);
        reservation.cancel(reservationCanceledStatus);

        SettlementEntry settlementEntry = settlementEntryRepository.findByReservation_Id(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_ENTRY_NOT_FOUND));

        // 상태이력의 previousStatus는 Status 테이블을 다시 조회하는 게 아니라, 지금 실제로 갖고 있던
        // 값을 그대로 캡처한다. "무슨 상태였는지"는 이 정산 항목의 진짜 이전 상태여야 하기 때문이다.
        Status previousSettlementStatus = settlementEntry.getStatus();
        Status settlementCanceledStatus = findStatus(StatusType.SETTLEMENT, CANCELED);
        settlementEntry.cancel(settlementCanceledStatus);

        SettlementStatusHistory history = SettlementStatusHistory.builder()
                .settlementEntry(settlementEntry)
                .previousStatus(previousSettlementStatus)
                .changedStatus(settlementCanceledStatus)
                .changeReason("예매 취소")
                .changedAt(LocalDateTime.now())
                .build();
        settlementStatusHistoryRepository.save(history);
    }

    private Status findStatus(StatusType type, String description) {
        return statusRepository.findByTypeAndDescription(type, description)
                .orElseThrow(() -> new BusinessException(ErrorCode.STATUS_NOT_FOUND));
    }

    private long calculateCommission(long grossAmount) {
        return BigDecimal.valueOf(grossAmount)
                .multiply(contractChargeRate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    private String generateReservationCode() {
        return "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
