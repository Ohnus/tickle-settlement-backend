package com.settlement.tickle.domain.member.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.settlement.tickle.domain.member.dto.request.MemberSignupRequestDto;
import com.settlement.tickle.domain.member.dto.response.MemberInfoResponseDto;
import com.settlement.tickle.domain.member.entity.MemberRoleType;
import com.settlement.tickle.domain.member.service.MemberService;
import com.settlement.tickle.global.auth.custom.CustomUserPrincipal;
import com.settlement.tickle.global.auth.filter.JwtFilter;
import com.settlement.tickle.global.exception.BusinessException;
import com.settlement.tickle.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * MemberController에 대한 슬라이스(slice) 테스트 — 웹 계층(MVC)만 띄운다.
 *
 * Service 테스트와의 차이:
 *   - Service 테스트: MemberService "안"의 로직을 직접 자바 코드로 호출해서 검증 (Repository만 가짜)
 *   - Controller 테스트: 실제 HTTP 요청을 MockMvc로 흉내내서 보낸다.
 *     "URL 라우팅이 맞는지, @Valid 검증이 걸리는지, 응답 JSON 모양이 맞는지, 예외가 GlobalExceptionHandler를 거쳐
 *     제대로 된 에러 응답으로 변환되는지"를 확인한다. MemberService 자체는 가짜로 대체하고,
 *     "그 가짜가 뭘 리턴하느냐"에 따라 컨트롤러가 응답을 잘 만드는지만 본다.
 *     DB는 여기서도 등장하지 않는다 — MemberService를 통째로 Mock으로 대체하기 때문에
 *     그 밑의 Repository/DB까지 갈 일이 아예 없다.
 */
@WebMvcTest(MemberController.class)
// @WebMvcTest(대상 컨트롤러.class): "이 컨트롤러 하나만 테스트할 거다"라고 명시.
// DispatcherServlet, @ControllerAdvice(GlobalExceptionHandler 포함), Validation, Jackson 등
// 웹 계층 관련된 것만 띄우고, @Service/@Repository 같은 나머지 빈은 전혀 안 만든다.
@AutoConfigureMockMvc(addFilters = false)
// addFilters = false: SecurityConfig(JwtFilter, 인가 규칙 등)는 이 슬라이스에 아예 안 실려있어서,
// 그냥 두면(addFilters=true) Spring Boot 기본 보안 설정(일단 전부 인증 필요)이 끼어들어 요청 자체가 막혀버린다(401 등).
// 지금은 "이 엔드포인트는 인증이 필요 없다(permitAll)"는 전제로 라우팅/검증/응답만 확인하는 과정이기 때문에,
// 필터 자체를 아예 안 태우도록 꺼둔다. (인증이 필요한 엔드포인트를 테스트할 땐 다른 방법을 씀 — 나중에 다룰 것)
class MemberControllerTest {

    @Autowired
    // MockMvc: 진짜 서버(Tomcat)를 띄우지 않고, HTTP 요청/응답을 흉내내주는 테스트 도구.
    private MockMvc mockMvc;

    @Autowired
    // ObjectMapper: 자바 객체 <-> JSON 문자열을 서로 변환해주는 Jackson의 핵심 클래스.
    // @WebMvcTest 슬라이스에도 Jackson 관련 설정은 포함되어 있어서 그대로 주입받아 쓸 수 있다.
    // signupApi()는 @RequestBody라 실제로 "JSON 문자열"을 요청 본문에 실어 보내야 하는데,
    // MemberSignupRequestDto 객체를 매번 손으로 JSON 문자열로 만들기 번거로우니 이걸로 자동 변환한다.
    private ObjectMapper objectMapper;

    @MockitoBean
    // @MockitoBean: 이 테스트의 Spring 컨텍스트 안에 있는 진짜 MemberService 자리를 Mockito 가짜 객체로 통째로 갈아끼운다.
    // (Service 테스트의 @Mock과 비슷하지만, @Mock은 Spring 컨텍스트 없이 순수 Mockito가 직접 만드는 거고,
    // @MockitoBean은 Spring 컨텍스트 "안"에 등록되는 빈 자체를 가짜로 바꿔치기한다는 점이 다르다.)
    private MemberService memberService;

    @MockitoBean
    // JwtFilter는 SecurityConfig가 아니라 @Component + Filter 구현체라서,
    // @WebMvcTest가 SecurityConfig는 안 가져오면서도 이 빈만큼은 자동으로 함께 스캔해서 만들려고 시도한다.
    // 근데 JwtFilter의 생성자가 요구하는 JwtUtil은 평범한 @Component라 이 슬라이스엔 없어서,
    // 실제로 만들려고 하면 컨텍스트 자체가 못 뜬다(NoSuchBeanDefinitionException).
    // 그래서 JwtFilter도 통째로 가짜로 대체해서 "일단 만들어지긴 하지만 안에 아무 로직도 없는" 상태로 둔다.
    // 어차피 @AutoConfigureMockMvc(addFilters = false)라 이 필터가 요청에 실제로 적용되지도 않는다.
    private JwtFilter jwtFilter;

    @Nested
    @DisplayName("GET /api/v1/members/email/exists는")
    class ExistsEmailApiTest {

        @Test
        @DisplayName("이메일이 존재하면 200과 data:true를 응답한다.")
        void returns200AndTrue_whenEmailExists() throws Exception {

            // given: 가짜 MemberService가 무조건 true를 리턴하도록 스텁.
            // existsByEmail()의 인자는 MemberExistsRequestDto 객체인데, 매번 정확한 값을 맞추기
            // 번거로우니 any()로 "어떤 값이 오든 true를 리턴해라"로 단순화했다.
            given(memberService.existsByEmail(any())).willReturn(true);

            // when & then
            // mockMvc.perform(get(...)): 실제로 GET 요청을 보내는 것처럼 흉내낸다.
            // .param("email", ...): 쿼리 파라미터를 붙인다.
            mockMvc.perform(get("/api/v1/members/email/exists")
                            .param("email", "user@example.com"))
                    // status().isOk(): HTTP 상태코드가 200인지 확인.
                    .andExpect(status().isOk())
                    // jsonPath(...): 응답 JSON 안의 특정 경로 값을 확인하는 문법.
                    // "$.data"는 응답 바디 최상위의 data 필드(ResultResponse의 data 필드)를 가리킨다.
                    .andExpect(jsonPath("$.data").value(true));
        }

        @Test
        @DisplayName("이메일이 존재하지 않으면 200과 data:false를 응답한다.")
        void returns200AndFalse_whenEmailNotExists() throws Exception {

            given(memberService.existsByEmail(any())).willReturn(false);

            mockMvc.perform(get("/api/v1/members/email/exists")
                            .param("email", "new-user@example.com"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(false));
        }

        @Test
        @DisplayName("이메일 형식이 올바르지 않으면 400을 응답한다.")
        void returns400_whenEmailFormatIsInvalid() throws Exception {

            // MemberService는 아예 호출되지 않아야 정상이다 — @Valid 검증에서 먼저 걸려야 하니까.
            // GlobalExceptionHandler의 MethodArgumentNotValidException 핸들러를 타고
            // {"status":400, "message":"...", "errors":[{"field":"email", ...}]} 형태로 응답한다.
            mockMvc.perform(get("/api/v1/members/email/exists")
                            .param("email", "not-an-email"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("email"));

            // 예외 응답 던지므로 서비스 로직 호출조차 되지 않는다.
            verify(memberService, never()).existsByEmail(any());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/members/nickname/exists는")
    class ExistsNicknameApiTest {

        @Test
        @DisplayName("Nickname이 존재하면 200과 data:true를 응답한다.")
        void returns200AndTrue_whenNicknameExists() throws Exception {

            // given: memberService.existsByNickname()에 any()를 던져서 true를 리턴하도록 세팅
            given(memberService.existsByNickname(any())).willReturn(true);

            // when: get(url)로 param 실어서 요청하면
            // then: 200과 true 응답
            mockMvc.perform(get("/api/v1/members/nickname/exists")
                    .param("nickname", "tickleHost"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(true));
        }

        @Test
        @DisplayName("Nickname이 존재하지 않으면 200과 data:false를 응답한다.")
        void returns200AndFalse_whenNicknameNotExists() throws Exception {

            given(memberService.existsByNickname(any())).willReturn(false);

            mockMvc.perform(get("/api/v1/members/nickname/exists")
                    .param("nickname", "newHost"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(false));
        }

        @Test
        @DisplayName("Nickname이 2자 미만일 경우 400을 응답한다.")
        void returns400_whenNicknameIsUnder2Characters() throws Exception {

            // 검증 예외이므로 given 없음
            // ErrorResponse의 형태는 status, message, errors(field, rejectedValue, reason)
            mockMvc.perform(get("/api/v1/members/nickname/exists")
                    .param("nickname", "a"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("nickname"));

            verify(memberService, never()).existsByNickname(any());
        }

        @Test
        @DisplayName("Nickname이 10자 초과일 경우 400을 응답한다.")
        void returns400_whenNicknameIsOver10Characters() throws Exception {

            mockMvc.perform(get("/api/v1/members/nickname/exists")
                    .param("nickname", "tickleHost1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("nickname"));

            verify(memberService, never()).existsByNickname(any());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/members/signup은")
    class SignupApiTest {

        @Test
        @DisplayName("요청이 유효하면 회원가입에 성공한다.")
        void succeeds_whenRequestIsValid() throws Exception {

            // given: signup()은 리턴 타입이 void라서, "성공하면 아무 일도 안 일어난다"가 기본값이다.
            // Mockito mock은 별도로 스텁하지 않으면 void 메서드는 그냥 아무것도 안 하고 끝나므로,
            // 이 테스트에서는 given(...)이 따로 필요 없다 — "예외 없이 잘 끝난다"는 상황 자체가 기본 동작.
            MemberSignupRequestDto requestDto = new MemberSignupRequestDto(
                    "user@example.com", "password123!", "tickle", MemberRoleType.MEMBER,
                    null, null, null, null, null
            );

            // when & then
            // post(url): GET과 달리 요청 본문(JSON)이 필요하다.
            // .contentType(MediaType.APPLICATION_JSON): "이 요청 본문은 JSON이다"라고 알려줌
            //   (컨트롤러의 @PostMapping(consumes = APPLICATION_JSON_VALUE)와 맞아야 함).
            // .content(objectMapper.writeValueAsString(requestDto)): DTO 객체를 실제 JSON 문자열로
            //   변환해서 요청 본문에 실음 — @RequestBody가 파싱할 대상이 바로 이 문자열이다.
            // 주의: ResultCode.MEMBER_CREATE_SUCCESS의 status 필드는 201(CREATED)이라 응답 바디에는
            // "status":201로 찍히지만, 컨트롤러가 ResponseEntity나 @ResponseStatus 없이 그냥
            // ResultResponse를 리턴하기만 해서 실제 HTTP 상태 코드는 Spring MVC 기본값인 200이 나간다.
            mockMvc.perform(post("/api/v1/members/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다."));
        }

        @Test
        @DisplayName("이메일 형식이 올바르지 않으면 400을 응답하고, Service는 호출되지 않는다.")
        void returns400_whenEmailFormatIsInvalid() throws Exception {

            // given: password/nickname/role은 정상, email만 형식이 틀림
            MemberSignupRequestDto requestDto = new MemberSignupRequestDto(
                    "not-an-email", "password123!", "tickle", MemberRoleType.MEMBER,
                    null, null, null, null, null
            );

            // when & then
            mockMvc.perform(post("/api/v1/members/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("email"));

            verify(memberService, never()).signup(any());
        }

        @Test
        @DisplayName("이미 사용 중인 이메일이면 409를 응답한다.")
        void returns409_whenEmailAlreadyExists() throws Exception {

            // given: MemberSignupRequestDto 자체는 형식상 문제가 없다(=검증은 통과한다).
            // "이메일 중복"은 DB를 봐야 아는 것이라 컨트롤러/DTO 검증이 아니라 Service가 판단하는 영역이다.
            // signup()이 void라서 "이 값을 리턴해라"가 아니라 "호출되면 이 예외를 던져라"로 스텁해야 하는데,
            // 이럴 땐 given().willReturn() 대신 willThrow(...).given(mock).메서드(...) 순서로 쓴다.
            MemberSignupRequestDto requestDto = new MemberSignupRequestDto(
                    "duplicate@example.com", "password123!", "tickle", MemberRoleType.MEMBER,
                    null, null, null, null, null
            );
            willThrow(new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS))
                    .given(memberService).signup(any());

            // when & then
            // 컨트롤러 코드에는 try/catch가 전혀 없지만, memberService.signup()이 던진 BusinessException을
            // GlobalExceptionHandler가 가로채서 ErrorCode.EMAIL_ALREADY_EXISTS에 맞는 409 응답으로
            // 바꿔주는지까지 이 테스트가 확인해준다.
            mockMvc.perform(post("/api/v1/members/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/members/me는")
    class MyInfoApiTest {

        // spring-security-test의 .with(authentication(...))은 원래 "실제 보안 필터 체인이
        // 요청을 처리하는 도중에" 끼어들어서 SecurityContext를 채워주는 방식으로 동작한다.
        // 근데 addFilters = false로 필터 체인 자체를 꺼놨기 때문에, 그 끼어들 지점이
        // 없어서 원래 방식대로는 안 먹혔다(실제로 돌려보니 principal이 null로 들어와서 NPE 발생).
        // 하지만 addFilters = true로 필터를 켜놓으면 인증이 필요하지 않은 테스트까지 모두 인증이 필요한 상황이 된다.
        // 그래서 한 단계 더 아래로 내려가서, @AuthenticationPrincipal이 최종적으로 읽는 대상인
        // SecurityContextHolder 자체를 테스트에서 직접 채워 넣는 방식으로 바꿨다.

        @AfterEach
        // 다음 테스트(다른 @Nested의 테스트 포함)에 이 인증 정보가 새어나가지 않도록,
        // 매 테스트가 끝나면 반드시 비워준다. SecurityContextHolder는 기본적으로 스레드 하나당
        // 하나 공유되는 저장소라, 안 지우면 상관없는 다른 테스트에서도 "인증된 상태"로 남아있게 된다.
        void clearSecurityContext() {
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("인증된 사용자면 200과 내 정보를 응답한다.")
        void returns200AndMemberInfo_whenAuthenticated() throws Exception {

            // given
            // 실제로는 JwtFilter가 유효한 Access Token을 검증한 뒤 이런 모양의 인증 객체를 만들어서
            // SecurityContextHolder에 넣어준다. 지금은 필터가 안 도니까, 그 결과물을 직접
            // 만들어서 "필터를 통과한 것처럼" SecurityContextHolder에 미리 채워 넣는다.
            // 즉, JwtFilter를 거치면서 만들어진 인증 객체를 컨트롤러에서 꺼내서 서비스로 넘기고 있는 상황 연출.
            // 아래 상황을 만들지 않으면 NPE가 터지면서 아래 에러가 뜬다.
            // Cannot invoke "com.settlement.tickle.global.auth.custom.CustomUserPrincipal.getUserId()" because "principal" is null
            CustomUserPrincipal principal = new CustomUserPrincipal(
                    1L, "host@example.com", List.of(new SimpleGrantedAuthority("ROLE_HOST"))
            );
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            MemberInfoResponseDto memberInfo = new MemberInfoResponseDto(
                    "host@example.com", "hostuser", MemberRoleType.HOST, null,
                    "123-45-67890", "티클컴퍼니", "국민은행", "홍길동", "123456-78-901234"
            );
            given(memberService.getMyInfo(1L)).willReturn(memberInfo);

            // when & then
            mockMvc.perform(get("/api/v1/members/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.email").value("host@example.com"))
                    .andExpect(jsonPath("$.data.nickname").value("hostuser"))
                    .andExpect(jsonPath("$.data.role").value("HOST"));
        }

        @Test
        @DisplayName("인증은 됐지만 DB에 회원이 없으면(탈퇴 등) 404를 응답한다.")
        void returns404_whenMemberNotFound() throws Exception {

            // given: 인증 자체는 정상이지만(=유효한 토큰이었지만),
            // 그 사이 회원이 탈퇴해서 DB에서는 더 이상 조회되지 않는 상황을 가정한다.
            CustomUserPrincipal principal = new CustomUserPrincipal(
                    999L, "ghost@example.com", List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
            );
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            given(memberService.getMyInfo(999L))
                    .willThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

            // when & then
            mockMvc.perform(get("/api/v1/members/me"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."));
        }
    }
}