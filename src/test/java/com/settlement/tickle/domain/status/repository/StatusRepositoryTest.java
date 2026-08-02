package com.settlement.tickle.domain.status.repository;

import com.settlement.tickle.domain.status.entity.Status;
import com.settlement.tickle.domain.status.entity.StatusType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
class StatusRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6");

    @Autowired
    private StatusRepository statusRepository;

    @Nested
    @DisplayName("findByTypeAndDescription()은")
    class FindByTypeAndDescriptionTest {

        @Test
        @DisplayName("타입과 설명이 일치하는 상태가 있으면 조회된다.")
        void returnsStatus_whenMatchExists() {

            // given: data.sql이 RESERVATION/RESERVED 상태를 이미 준비해둔다.
            // when
            Optional<Status> result = statusRepository.findByTypeAndDescription(StatusType.RESERVATION, "RESERVED");

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getType()).isEqualTo(StatusType.RESERVATION);
            assertThat(result.get().getDescription()).isEqualTo("RESERVED");
        }

        @Test
        @DisplayName("일치하는 상태가 없으면 빈 Optional을 반환한다.")
        void returnsEmpty_whenNoMatch() {

            // when
            Optional<Status> result = statusRepository.findByTypeAndDescription(StatusType.RESERVATION, "UNKNOWN");

            // then
            assertThat(result).isEmpty();
        }
    }
}
