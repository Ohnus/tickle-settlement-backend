package com.settlement.tickle.domain.reservation.entity;

import com.settlement.tickle.domain.member.entity.Member;
import com.settlement.tickle.domain.member.entity.MemberRoleType;
import com.settlement.tickle.domain.performance.entity.Performance;
import com.settlement.tickle.domain.status.entity.Status;
import com.settlement.tickle.domain.status.entity.StatusType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationTest {

    @Nested
    @DisplayName("cancel()은")
    class CancelTest {

        @Test
        @DisplayName("전달받은 상태로 변경한다.")
        void changesStatus_toGivenStatus() {

            // given
            Status reservedStatus = Status.builder().code(1).description("RESERVED").type(StatusType.RESERVATION).build();
            Status canceledStatus = Status.builder().code(2).description("CANCELED").type(StatusType.RESERVATION).build();

            Member member = Member.builder()
                    .email("buyer@example.com").password("encoded").nickname("buyer").role(MemberRoleType.MEMBER)
                    .build();
            Performance performance = Performance.builder()
                    .member(member).status(reservedStatus).title("콘서트").price(50000)
                    .startDate(LocalDateTime.now().plusDays(10)).endDate(LocalDateTime.now().plusDays(10).plusHours(2))
                    .reservationStartDate(LocalDateTime.now()).reservationEndDate(LocalDateTime.now().plusDays(9))
                    .build();
            Reservation reservation = Reservation.builder()
                    .member(member).performance(performance).status(reservedStatus).code("RES-TEST0001").price(50000)
                    .build();

            // when
            reservation.cancel(canceledStatus);

            // then
            assertThat(reservation.getStatus()).isEqualTo(canceledStatus);
        }
    }
}
