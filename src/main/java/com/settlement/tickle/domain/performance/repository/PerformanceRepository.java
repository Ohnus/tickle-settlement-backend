package com.settlement.tickle.domain.performance.repository;

import com.settlement.tickle.domain.performance.entity.Performance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {
}
