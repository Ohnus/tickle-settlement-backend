package com.settlement.tickle.domain.settlement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "batch_metadata")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatchMetadata {

    @Id
    @Column(name = "job_name", length = 100)
    private String jobName;

    @Column(name = "last_processed_at", nullable = false)
    private LocalDateTime lastProcessedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public BatchMetadata(String jobName, LocalDateTime lastProcessedAt, LocalDateTime updatedAt) {
        this.jobName = jobName;
        this.lastProcessedAt = lastProcessedAt;
        this.updatedAt = updatedAt;
    }
}