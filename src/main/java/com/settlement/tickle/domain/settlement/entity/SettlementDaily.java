package com.settlement.tickle.domain.settlement.entity;

import com.settlement.tickle.domain.member.entity.Member;
import com.settlement.tickle.domain.performance.entity.Performance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 참고: DDL의 uniq_settlement_daily_normal은 entry_type = 'NORMAL' 조건부(partial) 유니크 인덱스라
// Hibernate ddl-auto(update)로는 생성할 수 없음 — 필요 시 별도 마이그레이션 스크립트로 추가해야 함.
@Entity
@Table(name = "settlement_daily")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class SettlementDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "settlement_daily_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_id", nullable = false)
    private Performance performance;

    @Column(name = "performance_title", length = 50, nullable = false)
    private String performanceTitle;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", length = 10, nullable = false)
    private SettlementEntryType entryType;

    @Column(name = "sales_amount", nullable = false)
    private Long salesAmount;

    @Column(name = "refund_amount", nullable = false)
    private Long refundAmount;

    @Column(name = "gross_amount", nullable = false)
    private Long grossAmount;

    @Column(name = "commission", nullable = false)
    private Long commission;

    @Column(name = "net_amount", nullable = false)
    private Long netAmount;

    @CreatedDate
    @Column(name = "settlement_daily_created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public SettlementDaily(Member member, Performance performance, String performanceTitle, LocalDate settlementDate,
                            SettlementEntryType entryType, Long salesAmount, Long refundAmount, Long grossAmount,
                            Long commission, Long netAmount) {
        this.member = member;
        this.performance = performance;
        this.performanceTitle = performanceTitle;
        this.settlementDate = settlementDate;
        this.entryType = entryType != null ? entryType : SettlementEntryType.NORMAL;
        this.salesAmount = salesAmount;
        this.refundAmount = refundAmount != null ? refundAmount : 0L;
        this.grossAmount = grossAmount;
        this.commission = commission;
        this.netAmount = netAmount;
    }
}