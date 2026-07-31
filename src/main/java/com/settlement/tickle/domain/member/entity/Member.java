package com.settlement.tickle.domain.member.entity;

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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(name = "member_email", length = 30, nullable = false, unique = true)
    private String email;

    @Column(name = "member_pw", nullable = false)
    private String password;

    @Column(name = "member_nickname", length = 10, nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", length = 20, nullable = false)
    private MemberRoleType role;

    @CreatedDate
    @Column(name = "member_created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "member_updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "member_deleted_at")
    private LocalDateTime deletedAt;

    // 주최자(판매자) 정산 지급 관련 필드
    // 판매자 사업자번호
    @Column(name = "host_biz_number", length = 15)
    private String hostBizNumber;

    // 판매자 사업자명
    @Column(name = "host_biz_name", length = 15)
    private String hostBizName;

    // 판매자 정산 은행
    @Column(name = "host_biz_bank", length = 10)
    private String hostBizBank;

    // 판매자 계좌 예금주
    @Column(name = "host_biz_depositor", length = 10)
    private String hostBizDepositor;

    // 판매자 계좌번호
    @Column(name = "host_biz_bank_number", length = 25)
    private String hostBizBankNumber;

    @Builder
    public Member(String email, String password, String nickname, MemberRoleType role,
                  String hostBizNumber, String hostBizName, String hostBizBank,
                  String hostBizDepositor, String hostBizBankNumber) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.role = role != null ? role : MemberRoleType.HOST;
        this.hostBizNumber = hostBizNumber;
        this.hostBizName = hostBizName;
        this.hostBizBank = hostBizBank;
        this.hostBizDepositor = hostBizDepositor;
        this.hostBizBankNumber = hostBizBankNumber;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}