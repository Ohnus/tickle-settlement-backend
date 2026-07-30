package com.settlement.tickle.domain.performance.entity;

import com.settlement.tickle.domain.member.entity.Member;
import com.settlement.tickle.domain.status.entity.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "performance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Performance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "performance_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id", nullable = false)
    private Status status;

    @Column(name = "performance_title", length = 50, nullable = false)
    private String title;

    @Column(name = "performance_price", nullable = false)
    private Integer price;

    @Column(name = "performance_start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "performance_end_date", nullable = false)
    private LocalDateTime endDate;

    @CreatedDate
    @Column(name = "performance_created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Performance(Member member, Status status, String title, Integer price,
                        LocalDateTime startDate, LocalDateTime endDate) {
        this.member = member;
        this.status = status;
        this.title = title;
        this.price = price;
        this.startDate = startDate;
        this.endDate = endDate;
    }
}