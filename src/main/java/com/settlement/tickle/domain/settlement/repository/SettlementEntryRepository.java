package com.settlement.tickle.domain.settlement.repository;

import com.settlement.tickle.domain.settlement.entity.SettlementEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementEntryRepository extends JpaRepository<SettlementEntry, Long> {

    // 예매 취소 시 그 예매에 1:1로 묶인 건별 정산 항목을 찾기 위함
    Optional<SettlementEntry> findByReservation_Id(Long reservationId);
}
