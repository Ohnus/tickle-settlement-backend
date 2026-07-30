package com.settlement.tickle.domain.settlement.entity;

import com.settlement.tickle.domain.member.entity.Member;
import com.settlement.tickle.domain.performance.entity.Performance;
import com.settlement.tickle.domain.reservation.entity.Reservation;
import com.settlement.tickle.domain.status.entity.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlement_entry",
        indexes = @Index(name = "idx_settlement_entry_updated_at", columnList = "entry_updated_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class SettlementEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "settlement_entry_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id", nullable = false)
    private Status status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_id", nullable = false)
    private Performance performance;

    @Column(name = "performance_title", length = 50, nullable = false)
    private String performanceTitle;

    @Column(name = "performance_end_date", nullable = false)
    private LocalDateTime performanceEndDate;

    @Column(name = "sales_amount", nullable = false)
    private Long salesAmount;

    @Column(name = "refund_amount", nullable = false)
    private Long refundAmount;

    @Column(name = "gross_amount", nullable = false)
    private Long grossAmount;

    @Column(name = "contract_charge", nullable = false, precision = 4, scale = 3)
    private BigDecimal contractCharge;

    @Column(name = "commission", nullable = false)
    private Long commission;

    @Column(name = "net_amount", nullable = false)
    private Long netAmount;

    @CreatedDate
    @Column(name = "entry_created_at", nullable = false, updatable = false)
    private LocalDateTime entryCreatedAt;

    @LastModifiedDate
    @Column(name = "entry_updated_at", nullable = false)
    private LocalDateTime entryUpdatedAt;

    @Builder
    public SettlementEntry(Reservation reservation, Member member, Status status, Performance performance,
                            String performanceTitle, LocalDateTime performanceEndDate,
                            Long salesAmount, Long refundAmount, Long grossAmount,
                            BigDecimal contractCharge, Long commission, Long netAmount) {
        this.reservation = reservation;
        this.member = member;
        this.status = status;
        this.performance = performance;
        this.performanceTitle = performanceTitle;
        this.performanceEndDate = performanceEndDate;
        this.salesAmount = salesAmount;
        this.refundAmount = refundAmount != null ? refundAmount : 0L;
        this.grossAmount = grossAmount;
        this.contractCharge = contractCharge;
        this.commission = commission;
        this.netAmount = netAmount;
    }
}