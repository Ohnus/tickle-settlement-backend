package com.settlement.tickle.domain.member.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Member 엔티티에 대한 가장 기본적인 단위 테스트.
 *   Spring 컨텍스트를 전혀 띄우지 않는다(=@SpringBootTest 없음). DB도 안 쓴다.
 *   application-test.yaml(테스트 프로필)도 아직은 필요 없다.
 *   순수 자바 객체(Member)를 직접 만들어서 그 안의 로직만 확인하는 테스트라, Spring이 뜰 필요 자체가 없기 때문이다.
 *   (프로필은 나중에 Service/Controller/통합 테스트로 넘어갈 때 쓰게 된다.)
 *   Member.builder()로 객체를 직접 만들어서, 엔티티 안에 "직접 짠 로직"만 검증한다.
 *   getter처럼 롬복이 자동 생성해준 코드는 테스트하지 않는다. 검증할 로직 자체가 없기 때문이다.
 */
class MemberTest {

    // @Nested: 관련 있는 테스트끼리 안쪽 클래스로 묶는 용도.
    // JUnit5가 이 안쪽 클래스들도 전부 테스트로 인식해서 실행.
    // 실무에서 관례적으로 많이 쓴다.
    @Nested
    @DisplayName("isDeleted()는")
        // @DisplayName: 테스트 결과 리포트나 IDE 실행 창에 표시될 이름을 사람이 읽기 좋은 문장으로 지정한다.
        // 메서드 이름(returnsFalse_whenDeletedAtIsNull 같은)만으로는 한눈에 안 들어오니 실무에서 거의 필수로 쓴다.
    class IsDeletedTest {

        @Test
        // @Test: JUnit5에게 "이 메서드는 테스트다, 실행해라"라고 표시하는 어노테이션.
        // 이게 없으면 그냥 평범한 메서드라 테스트 실행 시 무시된다.
        @DisplayName("deletedAt이 없으면 false를 반환한다")
        void returnsFalse_whenDeletedAtIsNull() {

            // given: 테스트할 대상을 준비하는 단계.
            // Member 엔티티는 의도적으로 deletedAt을 builder에서 설정할 수 없게 막아뒀다(탈퇴 처리는 서비스 로직에서만 하도록 하려는 설계).
            // 그래서 아무것도 안 건드리면 deletedAt은 자연히 null인 상태가 된다.
            Member member = Member.builder()
                    .email("user@example.com")
                    .password("encoded-password")
                    .nickname("tickle")
                    .role(MemberRoleType.HOST)
                    .build();

            // when: 실제로 검증하고 싶은 동작을 실행하는 단계.
            boolean result = member.isDeleted();

            // then: 결과가 기대한 값인지 확인하는 단계.
            // assertThat(...): AssertJ 라이브러리 문법(spring-boot-starter-test에 이미 포함되어 있어 별도 추가 필요 없음).
            // "assertThat(실제값).isFalse()" 식으로 이어 써서 "실제값은 false여야 한다"를 사람이 읽는 문장처럼 표현한다.
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("builder()는")
    class BuilderTest {

        @Test
        @DisplayName("role을 지정하지 않으면 기본값을 HOST로 설정한다")
        void setsDefaultRole_whenRoleIsNull() {

            // given & when: role(...)을 아예 호출하지 않고 생성
            // Member 생성자 내부의 "role != null ? role : MemberRoleType.HOST" 분기를 타게 됨
            Member member = Member.builder()
                    .email("user@example.com")
                    .password("encoded-password")
                    .nickname("tickle")
                    .build();

            // then
            // isEqualTo(...): "실제값이 괄호 안의 값과 같아야 한다"는 뜻의 AssertJ 문법.
            assertThat(member.getRole()).isEqualTo(MemberRoleType.HOST);
        }

        @Test
        @DisplayName("role을 지정하면 그 값을 그대로 사용한다")
        void keepsGivenRole_whenRoleIsProvided() {

            Member member = Member.builder()
                    .email("host@example.com")
                    .password("encoded-password")
                    .nickname("hostuser")
                    .role(MemberRoleType.MEMBER)
                    .build();

            assertThat(member.getRole()).isEqualTo(MemberRoleType.MEMBER);
        }
    }
}