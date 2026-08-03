package com.settlement.tickle.domain.settlement.batch.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

public interface SettlementBatchMapper {

    // 건별(WAITING) -> 일간(NORMAL) 신규 집계.
    // 반환값은 INSERT된 (member, performance) 그룹 수.
    int aggregateDailyNormal(@Param("targetDate") LocalDate targetDate);
}
