package com.settlement.tickle.domain.settlement.batch.mapper;

import com.settlement.tickle.global.config.MyBatisConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

// @MybatisTest는 JPA 관련 빈을 안 띄우므로 테스트에 필요한 값은 JdbcTemplate으로 직접 INSERT한다.
// @MybatisTest는 슬라이스 테스트라 일반 @Configuration 클래스를 자동으로 스캔하지 않는다.
// 실제로 @Import(MyBatisConfig.class)를 빼고 돌려보면 SettlementBatchMapper 빈을 못 찾아 NoSuchBeanDefinitionException이 난다.
// @MapperScan이 이 슬라이스에 자동으로 안 잡힌다는 뜻이라 아래처럼 명시적으로 import 해야 한다.
@MybatisTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(MyBatisConfig.class)
@Testcontainers
class SettlementBatchMapperTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6");

    @Autowired
    private SettlementBatchMapper settlementBatchMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Integer statusId(String type, String description) {
        return jdbcTemplate.queryForObject(
                "SELECT status_id FROM status WHERE status_type = ? AND status_description = ?",
                Integer.class, type, description);
    }

    private Long insertMember(String email, String role) {
        jdbcTemplate.update(
                "INSERT INTO member (member_email, member_pw, member_nickname, member_role) VALUES (?, ?, ?, ?)",
                email, "encoded", "테스트", role);
        return jdbcTemplate.queryForObject(
                "SELECT member_id FROM member WHERE member_email = ?", Long.class, email);
    }

    private Long insertPerformance(Long hostId, String title) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO performance (member_id, status_id, performance_title, performance_price,
                                          performance_start_date, performance_end_date,
                                          performance_reservation_start_date, performance_reservation_end_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                hostId, statusId("PERFORMANCE", "ON_SALE"), title, 10000,
                now.plusDays(10), now.plusDays(10).plusHours(3), now, now.plusDays(9));
        return jdbcTemplate.queryForObject(
                "SELECT performance_id FROM performance WHERE performance_title = ?", Long.class, title);
    }

    private Long insertReservation(Long buyerId, Long performanceId, String code, long price) {
        jdbcTemplate.update("""
                INSERT INTO reservation (member_id, performance_id, status_id, reservation_code, reservation_price)
                VALUES (?, ?, ?, ?, ?)
                """,
                buyerId, performanceId, statusId("RESERVATION", "RESERVED"), code, price);
        return jdbcTemplate.queryForObject(
                "SELECT reservation_id FROM reservation WHERE reservation_code = ?", Long.class, code);
    }

    // 정산 대상은 구매자가 아니라 호스트이므로, 건별 정산의 member_id는 hostId로 넣는다.
    // (ReservationService.createReservation의 실제 규칙과 동일).
    private void insertSettlementEntry(Long reservationId, Long hostId, Long performanceId, String title,
                                       String settlementStatusDescription, long salesAmount, LocalDateTime createdAt) {
        long commission = Math.round(salesAmount * 0.05);
        jdbcTemplate.update("""
                INSERT INTO settlement_entry (reservation_id, member_id, status_id, performance_id, performance_title,
                                               performance_end_date, sales_amount, refund_amount, gross_amount,
                                               contract_charge, commission, net_amount, entry_created_at, entry_updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?)
                """,
                reservationId, hostId, statusId("SETTLEMENT", settlementStatusDescription), performanceId, title,
                createdAt.plusDays(10), salesAmount, salesAmount,
                new BigDecimal("0.050"), commission, salesAmount - commission, createdAt, createdAt);
    }

    @Nested
    @DisplayName("aggregateDailyNormal()은")
    class AggregateDailyNormalTest {

        @Test
        @DisplayName("대상 날짜의 WAITING 건들만 회원+공연 기준으로 합산해 NORMAL로 적재한다.")
        void aggregatesOnlyWaitingEntriesOfTargetDate() {

            // given
            LocalDate targetDate = LocalDate.of(2030, 6, 15);
            LocalDateTime onTargetDate = targetDate.atTime(10, 0);
            LocalDateTime onOtherDate = targetDate.minusDays(1).atTime(10, 0);

            Long host = insertMember("mapper-host@example.com", "HOST");
            Long buyer = insertMember("mapper-buyer@example.com", "MEMBER");
            Long performance = insertPerformance(host, "매퍼 테스트 공연");

            // 대상 날짜, WAITING 두 건 -> 합산 대상 (10000 + 20000 = 30000)
            insertSettlementEntry(insertReservation(buyer, performance, "SEED-A", 10000L),
                    host, performance, "매퍼 테스트 공연", "WAITING", 10000L, onTargetDate);
            insertSettlementEntry(insertReservation(buyer, performance, "SEED-B", 20000L),
                    host, performance, "매퍼 테스트 공연", "WAITING", 20000L, onTargetDate);

            // 대상 날짜, CANCELED 한 건 -> 제외 대상
            insertSettlementEntry(insertReservation(buyer, performance, "SEED-C", 99999L),
                    host, performance, "매퍼 테스트 공연", "CANCELED", 99999L, onTargetDate);

            // 다른 날짜, WAITING 한 건 -> 제외 대상
            insertSettlementEntry(insertReservation(buyer, performance, "SEED-D", 88888L),
                    host, performance, "매퍼 테스트 공연", "WAITING", 88888L, onOtherDate);

            // when
            int insertedGroupCount = settlementBatchMapper.aggregateDailyNormal(targetDate);

            // then
            assertThat(insertedGroupCount).isEqualTo(1);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM settlement_daily WHERE member_id = ? AND performance_id = ?", host, performance);
            assertThat(rows).hasSize(1);

            Map<String, Object> row = rows.get(0);
            assertThat(row.get("entry_type")).isEqualTo("NORMAL");
            assertThat(((Number) row.get("sales_amount")).longValue()).isEqualTo(30000L);
            assertThat(((Number) row.get("gross_amount")).longValue()).isEqualTo(30000L);
            assertThat(((Number) row.get("commission")).longValue()).isEqualTo(1500L);
            assertThat(((Number) row.get("net_amount")).longValue()).isEqualTo(28500L);
        }
    }
}
