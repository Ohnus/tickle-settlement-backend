package com.settlement.tickle.domain.member.service;

import com.settlement.tickle.domain.member.dto.request.MemberExistsRequestDto;
import com.settlement.tickle.domain.member.dto.response.MemberInfoResponseDto;
import com.settlement.tickle.domain.member.entity.Member;
import com.settlement.tickle.domain.member.entity.MemberRoleType;
import com.settlement.tickle.domain.member.repository.MemberRepository;
import com.settlement.tickle.global.auth.custom.CustomUserDetails;
import com.settlement.tickle.global.exception.BusinessException;
import com.settlement.tickle.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/*
 * MemberService에 대한 단위 테스트 — 엔티티 테스트(MemberTest)에서 한 단계 올라온 버전.
 *
 * 엔티티 테스트와 다른 점:
 * MemberService는 혼자 동작하지 않고 MemberRepository(=DB 접근)에 의존한다.
 * 단위 테스트에서는 진짜 DB를 붙이지 않고, MemberRepository를 "가짜"로 대체해서
 * MemberService 안의 로직(if문, 예외 처리 등)만 딱 떼어서 검증한다. 이게 Mockito가 하는 일.
 */
@ExtendWith(MockitoExtension.class)
// @ExtendWith(MockitoExtension.class): 이 테스트 클래스에서 Mockito 기능(@Mock, @InjectMocks 등)을
// 쓸 수 있게 JUnit5에 Mockito 확장 기능을 등록한다. 이게 없으면 @Mock/@InjectMocks가 그냥 null로 남는다.
class MemberServiceTest {

    @Mock
    // @Mock: 진짜 MemberRepository 대신 쓸 "가짜(mock)" 객체를 만들어달라는 표시.
    // 이 가짜 객체는 기본적으로 아무 로직이 없고, 정해준 대로만 동작한다(스텁 참고).
    // 즉 실제 DB에 쿼리를 날리지 않는다. 그래서 이 테스트는 DB 없이도 실행된다.
    private MemberRepository memberRepository;

    // 나중에 signup()을 테스트할 때 @Mock private PasswordEncoder passwordEncoder;를 추가.

    @InjectMocks
    // @InjectMocks: 위에서 만든 @Mock들을 MemberService의 생성자에 자동으로 꽂아서
    // "가짜 MemberRepository를 들고 있는 진짜 MemberService" 객체를 만들어준다.
    // (MemberService는 @RequiredArgsConstructor라 생성자 주입 방식으로 자동 매칭됨)
    private MemberService memberService;

    @Nested
    @DisplayName("existsByEmail()은")
    class ExistsByEmailTest {

        @Test
        @DisplayName("이메일이 비어있으면 INVALID_INPUT_VALUE 예외를 던지고, Repository는 호출하지 않는다")
        void throwsException_whenEmailIsBlank() {

            // given: email이 빈 문자열인 요청 DTO 준비 (nickname은 이 테스트와 무관하니 null)
            MemberExistsRequestDto requestDto = new MemberExistsRequestDto("", null);

            // when: catchThrowableOfType
            // "이 코드를 실행했을 때 던져지는 예외를, 지정한 타입으로 캐스팅해서 잡아라"는 AssertJ 유틸리티.
            // try-catch를 직접 안 써도 돼서 간결하다.
            BusinessException exception = catchThrowableOfType(
                    BusinessException.class,
                    () -> memberService.existsByEmail(requestDto)
            );

            // then: 잡은 예외가 null이 아니고(=실제로 던져졌고), errorcode가 기대한 값인지 확인.
            assertThat(exception).isNotNull();
            assertThat(exception.getErrorcode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

            // verify(mock, never()).메서드(...): "이 가짜 객체의 이 메서드는 한 번도 호출되지 않았어야 한다"는 뜻.
            // email 검증 단계에서 이미 예외가 터지므로, DB까지 갈 필요가 없다는 것.
            // 즉 short-circuit이 의도대로 동작하는지까지 같이 확인하는 것.
            // anyString()은 "인자가 어떤 문자열이든 상관없이"라는 뜻의 Mockito 매처(matcher).
            verify(memberRepository, never()).existsByEmail(anyString());
        }

        @Test
        @DisplayName("Repository가 이메일이 존재한다고 하면, 그대로 true를 반환한다")
        void returnsTrue_whenRepositoryReturnsTrue() {

            // given
            String email = "user@example.com";
            MemberExistsRequestDto requestDto = new MemberExistsRequestDto(email, null);

            // given(...).willReturn(...): BDDMockito 문법의 스텁(stub) 설정.
            // "가짜 memberRepository한테 existsByEmail(email)이 호출되면 true를 리턴하도록 미리 정해둔다"는 뜻.
            // 이 줄이 없으면 mock 메서드는 기본적으로 false(boolean 기본값)를 리턴한다.
            given(memberRepository.existsByEmail(email)).willReturn(true);

            // when: 실제 테스트 대상 메서드 호출
            boolean result = memberService.existsByEmail(requestDto);

            // then: Repository가 true라고 했으니 Service도 그대로 true를 리턴해야 한다
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Repository가 이메일이 없다고 하면, 그대로 false를 반환한다")
        void returnsFalse_whenRepositoryReturnsFalse() {

            // given
            String email = "new-user@example.com";
            MemberExistsRequestDto requestDto = new MemberExistsRequestDto(email, null);
            given(memberRepository.existsByEmail(email)).willReturn(false);

            // when
            boolean result = memberService.existsByEmail(requestDto);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("existsByNickname()은")
    class ExistsByNicknameTest {

        @Test
        @DisplayName("Nickname이 비어있으면 INVALID_INPUT_VALUE 예외를 던지고, Repository를 호출하지 않는다.")
        void throwsException_whenNicknameIsBlank() {

            // given
            MemberExistsRequestDto requestDto = new MemberExistsRequestDto(null, "");

            // when
            BusinessException exception = catchThrowableOfType(
                    BusinessException.class,
                    () -> memberService.existsByNickname(requestDto)
            );

            // then
            assertThat(exception).isNotNull();
            assertThat(exception.getErrorcode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

            verify(memberRepository, never()).existsByNickname(anyString());
        }

        @Test
        @DisplayName("Repository가 Nickname이 존재한다고 하면, true를 반환한다.")
        void returnsTrue_whenRepositoryReturnsTrue() {

            // given
            String nickname = "tickleHost";
            MemberExistsRequestDto requestDto = new MemberExistsRequestDto(null, nickname);

            // stub
            given(memberRepository.existsByNickname(nickname)).willReturn(true);

            // when
            boolean result = memberService.existsByNickname(requestDto);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Repository가 Nickname이 없다고 하면, false를 반환한다.")
        void returnsFalse_whenRepositoryReturnsFalse() {

            // given
            String nickname = "newTickleHost";
            MemberExistsRequestDto requestDto = new MemberExistsRequestDto(null, nickname);

            // stub
            given(memberRepository.existsByNickname(nickname)).willReturn(false);

            // when
            boolean result = memberService.existsByNickname(requestDto);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("getMyInfo()는")
    class GetMyInfoTest {

        @Test
        @DisplayName("Member ID로 조회했을 때 유저가 존재하지 않으면 MEMBER_NOT_FOUND 예외를 던진다.")
        void throwsException_whenMemberNotFound() {

            // given_stub
            // 리턴 타입이 null이 아닌 Optional<Member>이기 때문에 존재하지 않으면 Optional.empty()로 표현한다.
            Long memberId = 999L;
            given(memberRepository.findByIdAndDeletedAtIsNull(memberId)).willReturn(Optional.empty());

            // when
            BusinessException exception = catchThrowableOfType(
                    BusinessException.class,
                    () -> memberService.getMyInfo(memberId)
            );

            // then
            assertThat(exception).isNotNull();
            assertThat(exception.getErrorcode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        }

        @Test
        @DisplayName("Member ID로 조회했을 때 유저가 존재하면 MemberInfoResponseDto를 응답한다.")
        void returnsMemberInfoResponseDto_whenMemberExists() {

            // given
            Long memberId = 111L;
            Member member = Member.builder()
                    .email("user@example.com")
                    .password("password123!")
                    .nickname("tickleHost")
                    .role(MemberRoleType.HOST)
                    .hostBizNumber("123-456-789")
                    .hostBizName("티클컴퍼니")
                    .hostBizBank("토스뱅크")
                    .hostBizDepositor("김티클")
                    .hostBizBankNumber("123456789-12-345")
                    .build();

            // stub
            // 리턴 타입이 Optional<Member>이므로 Optional.of()를 사용하여 stub한다.
            given(memberRepository.findByIdAndDeletedAtIsNull(memberId)).willReturn(Optional.of(member));

            // when
            MemberInfoResponseDto result = memberService.getMyInfo(memberId);

            // then
            // 원본 Member의 값과 하나씩 비교한다.
            assertThat(result.email()).isEqualTo(member.getEmail());
            assertThat(result.nickname()).isEqualTo(member.getNickname());
            assertThat(result.role()).isEqualTo(member.getRole());
            assertThat(result.hostBizNumber()).isEqualTo(member.getHostBizNumber());
            assertThat(result.hostBizName()).isEqualTo(member.getHostBizName());
            assertThat(result.hostBizBank()).isEqualTo(member.getHostBizBank());
            assertThat(result.hostBizDepositor()).isEqualTo(member.getHostBizDepositor());
            assertThat(result.hostBizBankNumber()).isEqualTo(member.getHostBizBankNumber());
        }
    }

    @Nested
    @DisplayName("loadUserByUsername()은")
    class LoadUserByUsernameTest {

        @Test
        @DisplayName("이메일로 회원을 찾으면 CustomUserDetails로 변환해서 UserDetails를 반환한다.")
        void returnsUserDetails_whenMemberExists() {

            // given
            String email = "user@example.com";
            Member member = Member.builder()
                    .email(email)
                    .password("password123!")
                    .nickname("tickleHost")
                    .role(MemberRoleType.HOST)
                    .build();

            // stub
            given(memberRepository.findByEmailAndDeletedAtIsNull(email)).willReturn(Optional.of(member));

            // when
            // 변환(실제 구현 객체)은 CustomUserDetails로 되지만 반환 타입은 UserDetails다.
            UserDetails result = memberService.loadUserByUsername(email);

            // then
            // isInstanceOf()는 선언된 타입이 아니라 실제로 들어있는 구현체 타입까지 확인할 때 사용한다.
            assertThat(result).isInstanceOf(CustomUserDetails.class);
            assertThat(result.getUsername()).isEqualTo(member.getEmail());
            assertThat(result.getPassword()).isEqualTo(member.getPassword());
            // CustomUserDetails는 권한 앞에 ROLE_ 접두사를 붙여서 저장한다.
            // extracting()은 컬렉션 안의 특정 값만 뽑아내서 비교하고 싶을 때 사용하는 AssertJ 문법.
            // GrantedAuthority 객체 목록에서 getAuthority() 문자열만 뽑아서 비교한다.
            assertThat(result.getAuthorities())
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_HOST");
        }

        @Test
        @DisplayName("이메일로 회원을 찾지 못하면 UsernameNotFoundException을 던진다.")
        void throwsUsernameNotFoundException_whenMemberNotFound() {

            // given_stub
            String email = "user@example.com";
            given(memberRepository.findByEmailAndDeletedAtIsNull(email)).willReturn(Optional.empty());

            // when
            // BusinessException이 아닌 UsernameNotFoundException 잡는다.
            UsernameNotFoundException exception = catchThrowableOfType(
                    UsernameNotFoundException.class,
                    () -> memberService.loadUserByUsername(email)
            );

            // then
            assertThat(exception).isNotNull();
        }
    }
}