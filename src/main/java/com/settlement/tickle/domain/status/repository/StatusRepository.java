package com.settlement.tickle.domain.status.repository;

import com.settlement.tickle.domain.status.entity.Status;
import com.settlement.tickle.domain.status.entity.StatusType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StatusRepository extends JpaRepository<Status, Integer> {

    // 상태값은 코드가 아니라 (type, description) 조합으로 조회한다. status_id 자체는
    // 더미 저장 순서에 따라 바뀔 수 있어서 비즈니스 로직에서 하드코딩할 값이 못 된다.
    Optional<Status> findByTypeAndDescription(StatusType type, String description);
}
