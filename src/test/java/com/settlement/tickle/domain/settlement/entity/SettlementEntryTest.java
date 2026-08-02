package com.settlement.tickle.domain.settlement.entity;

import com.settlement.tickle.domain.member.entity.Member;
import com.settlement.tickle.domain.member.entity.MemberRoleType;
import com.settlement.tickle.domain.performance.entity.Performance;
import com.settlement.tickle.domain.reservation.entity.Reservation;
import com.settlement.tickle.domain.status.entity.Status;
import com.settlement.tickle.domain.status.entity.StatusType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementEntryTest {

    @Nested
    @DisplayName("cancel()은")
    class CancelTest {

        @Test
        @DisplayName("전달받은 상태로 변경한다.")
        void changesStatus_toGivenStatus() {

            // given
            Status waitingStatus = Status.builder().code(1).description("WAITING").type(StatusType.SETTLEMENT).build();
            Status canceledStatus = Status.builder().code(2).description("CANCELED").type(StatusType.SETTLEMENT).build();

            Member host = Member.builder()
                    .email("host@example.com").password("encoded").nickname("host").role(MemberRoleType.HOST)
                    .build();
            Performance performance = Performance.builder()
                    .member(host).status(waitingStatus).title("콘서트").price(50000)
                    .startDate(LocalDateTime.now().plusDays(10)).endDate(LocalDateTime.now().plusDays(10).plusHours(2))
                    .build();
            Reservation reservation = Reservation.builder()
                    .member(host).performance(performance).status(waitingStatus).code("RES-TEST0001").price(50000)
                    .build();
            SettlementEntry settlementEntry = SettlementEntry.builder()
                    .reservation(reservation).member(host).status(waitingStatus).performance(performance)
                    .performanceTitle("콘서트").performanceEndDate(performance.getEndDate())
                    .salesAmount(50000L).refundAmount(0L).grossAmount(50000L)
                    .contractCharge(new BigDecimal("0.050")).commission(2500L).netAmount(47500L)
                    .build();

            // when
            settlementEntry.cancel(canceledStatus);

            // then
            assertThat(settlementEntry.getStatus()).isEqualTo(canceledStatus);
        }
    }
}
