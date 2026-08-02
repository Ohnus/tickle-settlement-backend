package com.settlement.tickle.domain.reservation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.settlement.tickle.domain.reservation.dto.request.ReservationCreateRequestDto;
import com.settlement.tickle.domain.reservation.dto.response.ReservationCreateResponseDto;
import com.settlement.tickle.domain.reservation.service.ReservationService;
import com.settlement.tickle.global.auth.custom.CustomUserPrincipal;
import com.settlement.tickle.global.auth.filter.JwtFilter;
import com.settlement.tickle.global.exception.BusinessException;
import com.settlement.tickle.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReservationControllerTest {

    private static final Long MEMBER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReservationService reservationService;

    @MockitoBean
    private JwtFilter jwtFilter;

    // 두 엔드포인트 모두 인증이 필요해서(anyRequest().authenticated()), 매 테스트마다 반복하지 않도록
    // 클래스 레벨에서 한 번만 SecurityContext를 채워둔다.
    @BeforeEach
    void setUpAuthentication() {
        CustomUserPrincipal principal = new CustomUserPrincipal(
                MEMBER_ID, "buyer@example.com", List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("POST /api/v1/reservations는")
    class CreateReservationTest {

        @Test
        @DisplayName("유효한 요청이면 201과 예매 정보를 응답한다.")
        void returns201_whenValid() throws Exception {

            // given
            ReservationCreateResponseDto response = new ReservationCreateResponseDto(
                    1L, "RES-ABCD1234", "콘서트", 50000, "RESERVED"
            );
            given(reservationService.createReservation(eq(MEMBER_ID), any())).willReturn(response);

            // when & then
            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ReservationCreateRequestDto(1L))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.reservationCode").value("RES-ABCD1234"))
                    .andExpect(jsonPath("$.data.status").value("RESERVED"));
        }

        @Test
        @DisplayName("공연 ID가 없으면 400을 응답하고, Service는 호출되지 않는다.")
        void returns400_whenPerformanceIdIsMissing() throws Exception {

            // when & then
            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("performanceId"));

            verify(reservationService, never()).createReservation(any(), any());
        }

        @Test
        @DisplayName("존재하지 않는 공연이면 404를 응답한다.")
        void returns404_whenPerformanceNotFound() throws Exception {

            // given
            given(reservationService.createReservation(eq(MEMBER_ID), any()))
                    .willThrow(new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND));

            // when & then
            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ReservationCreateRequestDto(999L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("공연을 찾을 수 없습니다."));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/reservations/{id}/cancel은")
    class CancelReservationTest {

        @Test
        @DisplayName("정상 취소되면 200을 응답한다.")
        void returns200_whenValid() throws Exception {

            // when & then
            mockMvc.perform(patch("/api/v1/reservations/{id}/cancel", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("예매가 취소되었습니다."));
        }

        @Test
        @DisplayName("본인의 예매가 아니면 403을 응답한다.")
        void returns403_whenNotOwner() throws Exception {

            // given
            willThrow(new BusinessException(ErrorCode.RESERVATION_ACCESS_DENIED))
                    .given(reservationService).cancelReservation(eq(MEMBER_ID), eq(1L));

            // when & then
            mockMvc.perform(patch("/api/v1/reservations/{id}/cancel", 1L))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("이미 취소된 예매면 409를 응답한다.")
        void returns409_whenAlreadyCanceled() throws Exception {

            // given
            willThrow(new BusinessException(ErrorCode.RESERVATION_ALREADY_CANCELED))
                    .given(reservationService).cancelReservation(eq(MEMBER_ID), eq(1L));

            // when & then
            mockMvc.perform(patch("/api/v1/reservations/{id}/cancel", 1L))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("취소 기한이 지났으면 400을 응답한다.")
        void returns400_whenCancelPeriodExpired() throws Exception {

            // given
            willThrow(new BusinessException(ErrorCode.RESERVATION_CANCEL_PERIOD_EXPIRED))
                    .given(reservationService).cancelReservation(eq(MEMBER_ID), eq(1L));

            // when & then
            mockMvc.perform(patch("/api/v1/reservations/{id}/cancel", 1L))
                    .andExpect(status().isBadRequest());
        }
    }
}
