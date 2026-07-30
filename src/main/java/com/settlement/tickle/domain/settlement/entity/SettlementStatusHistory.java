package com.settlement.tickle.domain.settlement.entity;

import com.settlement.tickle.domain.status.entity.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "settlement_status_history",
        indexes = @Index(name = "idx_settlement_status_history_entry", columnList = "settlement_entry_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "settlement_status_history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_entry_id", nullable = false)
    private SettlementEntry settlementEntry;

    // 최초 생성 시 NULL (전이 이전 상태가 없음)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_status_id")
    private Status previousStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_status_id", nullable = false)
    private Status changedStatus;

    @Column(name = "change_reason", length = 100)
    private String changeReason;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Builder
    public SettlementStatusHistory(SettlementEntry settlementEntry, Status previousStatus, Status changedStatus,
                                    String changeReason, LocalDateTime changedAt) {
        this.settlementEntry = settlementEntry;
        this.previousStatus = previousStatus;
        this.changedStatus = changedStatus;
        this.changeReason = changeReason;
        this.changedAt = changedAt != null ? changedAt : LocalDateTime.now();
    }
}