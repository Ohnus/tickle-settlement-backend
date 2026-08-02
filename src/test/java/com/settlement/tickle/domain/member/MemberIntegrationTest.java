package com.settlement.tickle.domain.member;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.settlement.tickle.domain.member.dto.request.MemberSignupRequestDto;
import com.settlement.tickle.domain.member.entity.Member;
import com.settlement.tickle.domain.member.entity.MemberRoleType;
import com.settlement.tickle.domain.member.repository.MemberRepository;
import com.settlement.tickle.global.auth.jwt.util.JwtTokenType;
import com.settlement.tickle.global.auth.jwt.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * Member 도메인에 대한 "Controller ~ DB" 통합/E2E 테스트
 *
 * 지금까지와 다른 점: Mock이 정말 하나도 없다.
 * MemberController, MemberService, MemberRepository, SecurityConfig, JwtFilter까지 전부 진짜 빈으로 뜨고,
 * DB도 진짜 Postgres(Testcontainers)다.
 *
 * 특히 이 테스트를 만든 핵심 이유:
 * 지금까지의 Controller 슬라이스 테스트는 @AutoConfigureMockMvc(addFilters = false)로 보안 필터 체인을 아예 꺼놓고
 * SecurityContext를 직접 채워 넣는 방식이었다.
 * 그래서 "진짜 JwtFilter가 진짜 토큰을 검증하는지", "permitAll/ authenticated() 인가 규칙이 실제로 지켜지는지"는
 * 여태 단 한 번도 검증된 적이 없다. 이 테스트가 그 구멍을 메운다.
 * @AutoConfigureMockMvc의 addFilters 기본값(true)을 그대로 둬서 필터를 다 살린다.
 */
@SpringBootTest
// @SpringBootTest: 컨텍스트를 슬라이스로 자르지 않고 애플리케이션 전체를 그대로 띄운다.
@AutoConfigureMockMvc
// addFilters를 따로 false로 끄지 않는다. 이번엔 SecurityConfig가 등록한 진짜 필터 체인을 그대로 태운다.
@ActiveProfiles("test")
// application-test.yaml을 활성화한다.
// JwtUtil이 @Value로 요구하는 JWT 시크릿/만료시간이 이 프로필에서 온다. (DB 접속 정보는 여전히 Testcontainers/@ServiceConnection이 담당)
@Testcontainers
class MemberIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    // 주의: @DataJpaTest와 달리 @SpringBootTest는 테스트마다 자동 롤백을 해주지 않는다.
    // 그래서 테스트끼리 데이터가 안 겹치도록, 메서드마다 서로 다른 이메일을 쓴다.
    // (같은 컨테이너/DB를 이 클래스의 모든 테스트가 공유하기 때문).

    @Nested
    @DisplayName("회원가입은")
    class SignupFlowTest {

        @Test
        @DisplayName("성공하면 실제 DB에 암호화된 비밀번호로 저장된다.")
        void savesRealRowInDatabase() throws Exception {

            // given
            MemberSignupRequestDto requestDto = new MemberSignupRequestDto(
                    "integration@example.com", "password123!", "integUser", MemberRoleType.MEMBER,
                    null, null, null, null, null
            );

            // when: MemberService가 이번엔 진짜라서, 실제로 비밀번호 암호화 + DB 저장까지 다 일어난다.
            mockMvc.perform(post("/api/v1/members/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andExpect(status().isCreated());

            // then
            // Controller 슬라이스 테스트에서는 MemberService가 Mock이라 "save()가 호출됐는지"만
            // 확인할 수 있었는데, 여기서는 DB를 다시 조회해서 "진짜로 저장까지 됐는지" 직접 확인 가능.
            Member savedMember = memberRepository.findByEmailAndDeletedAtIsNull("integration@example.com")
                    .orElseThrow();
            assertThat(savedMember.getNickname()).isEqualTo("integUser");
            // 원본 비밀번호가 그대로 저장되지 않고 실제로 암호화됐는지까지 확인한다.
            assertThat(passwordEncoder.matches("password123!", savedMember.getPassword())).isTrue();
        }
    }

    @Nested
    @DisplayName("인증이 필요 없는 API는")
    class PublicApiTest {

        @Test
        @DisplayName("토큰 없이도 정상 호출된다 (permitAll 규칙이 실제로 지켜지는지 확인)")
        void existsEmailApi_worksWithoutToken() throws Exception {

            // 지금까지는 필터 자체를 꺼놓고 테스트해서 SecurityConfig의
            // authorizeHttpRequests 규칙이 실제로 이 경로를 permitAll로 봐주는지 검증된 적이 없었다.
            mockMvc.perform(get("/api/v1/members/email/exists")
                            .param("email", "someone@example.com"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/members/me는")
    class MyInfoApiTest {

        @Test
        @DisplayName("토큰 없이 호출하면 401을 응답한다")
        void returns401_withoutToken() throws Exception {

            // Controller 슬라이스 테스트에서는 addFilters = false 때문에
            // 이 케이스 자체를 테스트할 방법이 없었다(필터가 없으니 인가 규칙도 안 먹힘).
            // 이제 진짜 필터가 살아있어서 확인 가능하다.
            mockMvc.perform(get("/api/v1/members/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("진짜 발급받은 토큰으로 호출하면 실제 DB의 내 정보를 응답한다")
        void returnsRealData_withValidToken() throws Exception {

            // given: 먼저 실제 회원을 하나 저장하고
            Member member = memberRepository.save(Member.builder()
                    .email("token-user@example.com")
                    .password(passwordEncoder.encode("password123!"))
                    .nickname("tokenUser")
                    .role(MemberRoleType.HOST)
                    .build());

            // JwtUtil로 그 회원의 id가 담긴 진짜 Access Token을 발급한다. Mock이 아니라 실제 서명된 JWT.
            String accessToken = jwtUtil.createJwt(
                    member.getId(), member.getEmail(), "ROLE_HOST", JwtTokenType.ACCESS
            );

            // when & then
            // Authorization 헤더에 진짜 토큰을 실으면:
            // 진짜 JwtFilter가 검증 → SecurityContext에 CustomUserPrincipal 채움
            // → 컨트롤러의 @AuthenticationPrincipal이 받음 → 진짜 Service가 진짜 Repository로 진짜 DB를 조회 → 응답.
            // 전체 스택이 한 번에 다 확인되는 지점이다.
            mockMvc.perform(get("/api/v1/members/me")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.email").value("token-user@example.com"))
                    .andExpect(jsonPath("$.data.nickname").value("tokenUser"));
        }
    }
}
