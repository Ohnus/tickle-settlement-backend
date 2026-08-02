package com.settlement.tickle.domain.settlement.repository;

import com.settlement.tickle.domain.settlement.entity.SettlementStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementStatusHistoryRepository extends JpaRepository<SettlementStatusHistory, Long> {
}
