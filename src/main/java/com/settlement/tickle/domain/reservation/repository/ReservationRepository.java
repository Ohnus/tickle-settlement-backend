package com.settlement.tickle.domain.reservation.repository;

import com.settlement.tickle.domain.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
}
