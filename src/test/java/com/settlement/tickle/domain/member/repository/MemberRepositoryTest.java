package com.settlement.tickle.domain.member.repository;

import com.settlement.tickle.domain.member.entity.Member;
import com.settlement.tickle.domain.member.entity.MemberRoleType;
import com.settlement.tickle.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/*
 * MemberRepository에 대한 슬라이스/통합 테스트 — 지금까지와 가장 크게 다른 점은 Mock이 하나도 없다는 것.
 *
 * @DataJpaTest가 진짜 Postgres(Testcontainers로 띄운 도커 컨테이너)에 실제로 붙어서,
 * save()/findBy...() 같은 메서드가 진짜 SQL을 만들어 실행하고 그 결과를 그대로 돌려준다.
 * 그래서 "JPA 매핑(@Column 이름, 타입 등)이 실제로 맞는지", "@CreatedDate 같은 자동 채움이
 * 실제로 동작하는지"까지 여기서 처음으로 확인할 수 있다.
 * Mockito 단위 테스트에서는 Repository 자체가 가짜였기 때문에 절대 볼 수 없었던 부분이다.
 *
 * 각 @Test 메서드는 기본적으로 트랜잭션 안에서 실행되고 끝나면 자동으로 롤백된다 (@DataJpaTest의 기본 동작).
 * 그래서 여러 테스트가 같은 컨테이너/DB를 공유해도 서로 데이터가 섞이지 않는다.
 * Controller 테스트에서 SecurityContextHolder를 수동으로 비워줘야 했던 것과 달리, 여기는 별도 정리 코드가 필요 없다.
 */
@DataJpaTest
// @DataJpaTest: JPA 관련된 것(엔티티, Repository, EntityManager, DataSource)만 띄우는 슬라이스.
// @Service/@Controller는 안 뜬다 — @WebMvcTest가 웹 계층만 잘라 띄웠던 것과 같은 원리, 대상만 다르다.
@AutoConfigureTestDatabase(replace = Replace.NONE)
// @DataJpaTest는 기본적으로 진짜 DB 대신 인메모리 H2로 몰래 바꿔치기한다.
// 그걸 막고 Testcontainers로 띄운 진짜 Postgres를 그대로 쓰겠다고 명시하는 설정.
@Import(JpaAuditingConfig.class)
// JpaAuditingConfig(@EnableJpaAuditing)도 SecurityConfig처럼 평범한 @Configuration이라
// 슬라이스에 자동으로 안 딸려올 수 있어서, @CreatedDate/@LastModifiedDate가 실제로 동작하게 하려면 명시적으로 가져와야 한다.
@Testcontainers
// @Testcontainers: JUnit5 확장 — 아래 @Container가 붙은 필드의 생명주기(시작/종료)를 관리해준다.
class MemberRepositoryTest {

    @Container
    // @Container: 이 필드가 테스트에서 쓸 컨테이너라고 표시.
    @ServiceConnection
    // @ServiceConnection: 이 컨테이너의 접속 정보(URL, 계정 등)를 별도 설정 없이 Spring의
    // DataSource에 자동으로 연결해준다(Spring Boot 3.1+ 기능).
    // PostgreSQLContainer 클래스 자체가 기본 정보(DB명, 유저명, 비밀번호: test)를 하드 코딩된 기본값을 갖고 있다. 포트는 랜덤.
    // 예전엔 @DynamicPropertySource로 URL을 손수 등록해야 했는데, 이게 그 수고를 대신해준다.
    // static인 이유: 테스트 메서드마다 컨테이너를 새로 띄우면 너무 느려서,
    // 이 클래스의 모든 테스트가 컨테이너 하나를 공유하게 한다(클래스당 한 번만 뜬다).
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6");

    @Autowired
    private MemberRepository memberRepository;

    @Nested
    @DisplayName("save()는")
    class SaveTest {

        @Test
        @DisplayName("저장하면 id와 생성시각(createdAt)이 채워진다.")
        void assignsIdAndCreatedAt_whenSaved() {

            // given
            Member member = Member.builder()
                    .email("host@example.com")
                    .password("encoded-password")
                    .nickname("hostuser")
                    .role(MemberRoleType.HOST)
                    .build();

            // when: 실제로 INSERT 쿼리가 나간다.
            Member savedMember = memberRepository.save(member);

            // then
            // 단위 테스트(MemberTest)에서는 순수 자바 객체로만 만들어서 id/createdAt이 항상 null이었는데,
            // 여기서는 진짜로 DB에 저장되고 나서 그 값들이 채워져 돌아오는 걸 확인할 수 있다.
            assertThat(savedMember.getId()).isNotNull();
            assertThat(savedMember.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("existsByEmail()은")
    class ExistsByEmailTest {

        @Test
        @DisplayName("저장된 이메일이면 true를 반환한다.")
        void returnsTrue_whenEmailExists() {

            memberRepository.save(Member.builder()
                    .email("user@example.com")
                    .password("encoded-password")
                    .nickname("tickle")
                    .role(MemberRoleType.MEMBER)
                    .build());

            boolean result = memberRepository.existsByEmail("user@example.com");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("저장되지 않은 이메일이면 false를 반환한다")
        void returnsFalse_whenEmailNotExists() {

            boolean result = memberRepository.existsByEmail("nobody@example.com");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("findByEmailAndDeletedAtIsNull()은")
    class FindByEmailAndDeletedAtIsNullTest {

        @Test
        @DisplayName("탈퇴하지 않은 회원이면 조회된다.")
        void returnsMember_whenNotDeleted() {

            memberRepository.save(Member.builder()
                    .email("active@example.com")
                    .password("encoded-password")
                    .nickname("activeUser")
                    .role(MemberRoleType.MEMBER)
                    .build());

            Optional<Member> result = memberRepository.findByEmailAndDeletedAtIsNull("active@example.com");

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("탈퇴한(deletedAt이 채워진) 회원이면 조회되지 않는다.")
        void returnsEmpty_whenDeleted() {

            // given
            Member member = Member.builder()
                    .email("withdrawn@example.com")
                    .password("encoded-password")
                    .nickname("delUser") // member_nickname 컬럼은 VARCHAR(10) — 10자를 넘기면 안 됨
                    .role(MemberRoleType.MEMBER)
                    .build();

            // Member 엔티티는 deletedAt을 builder로 못 채우게 의도적으로 막아뒀다(탈퇴 처리는 아직 없는 서비스 로직의 몫).
            // 그래서 private 필드를 강제로 채워 넣는 ReflectionTestUtils로 "이미 탈퇴한 상태"를 흉내낸다.
            ReflectionTestUtils.setField(member, "deletedAt", LocalDateTime.now());
            memberRepository.save(member);

            // when
            Optional<Member> result = memberRepository.findByEmailAndDeletedAtIsNull("withdrawn@example.com");

            // then: 쿼리 메서드 이름 그대로 deletedAt이 null인 것만 찾으므로, 이 회원은 안 잡혀야 한다.
            assertThat(result).isEmpty();
        }
    }
}
