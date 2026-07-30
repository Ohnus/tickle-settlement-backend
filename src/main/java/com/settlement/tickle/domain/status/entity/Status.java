package com.settlement.tickle.domain.status.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "status")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Status {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "status_id")
    private Integer id;

    @Column(name = "status_code", nullable = false)
    private Integer code;

    @CreatedDate
    @Column(name = "status_created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "status_description", length = 20, nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_type", length = 20, nullable = false)
    private StatusType type;

    @Builder
    public Status(Integer code, String description, StatusType type) {
        this.code = code;
        this.description = description;
        this.type = type;
    }
}