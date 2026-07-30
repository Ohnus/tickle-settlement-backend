# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

`tickle`(그룹 `com.settlement`)은 티켓 정산 플랫폼의 백엔드입니다. 공연(performance), 예매(reservation), 정산(settlement — 주최자/판매자에게 지급하는 수수료 기반 정산, 수수료율은 호스트별 협상 없이 `application.yaml`의 `host.contract.charge-rate`(현재 0.05) 고정값 하나를 전 판매자에게 동일하게 적용)을 다룹니다. Spring Boot 3.5, Java 21, Gradle 기반입니다.

**이 코드베이스는 초기 개발 단계입니다.** `src/main/java/com/settlement/tickle/domain/` 아래 `performance`, `reservation`, `settlement`(하위에 `batch` 패키지 포함), `status` 패키지는 controller/dto/entity/repository/service 디렉터리만 만들어져 있고 아직 실제 코드는 없습니다. `member` 도메인은 `entity` 계층(`Member`, `MemberRoleType`)만 구현되어 있고 controller/dto/repository/service는 아직 없습니다. 반면 `global/`(인증, 설정, 예외 처리, 응답 래퍼)은 도메인 계층보다 먼저 구현되어 있습니다.

## 커맨드

Windows 환경 — `gradlew.bat`을 사용하세요(Git Bash에서는 `./gradlew`도 가능).

```
gradlew.bat build            # 전체 빌드
gradlew.bat test             # 전체 테스트 실행
gradlew.bat test --tests "com.settlement.tickle.TickleApplicationTests"   # 단일 테스트 클래스
gradlew.bat test --tests "*.TickleApplicationTests.contextLoads"          # 단일 테스트 메서드
gradlew.bat bootRun          # 로컬 실행 (포트 8080)
gradlew.bat jacocoTestReport # 테스트 후 커버리지 리포트(html) 생성
```

로컬 인프라(Postgres 17.6 + Redis 7.2)는 Docker Compose로 띄웁니다.

```
docker-compose up -d
```

`.env` 파일이 필요합니다(`.env.example` 참고: DB_*, REDIS_*, JWT_* 키). `me.paulschwarz:springboot3-dotenv`로 로드되어 `application.yaml`에서 `${VAR}` 형태로 참조됩니다. `.env`는 gitignore 대상이며, 실제 비밀값을 커밋하지 마세요.

## 아키텍처

- **패키지 구조**: `domain/<feature>/{controller,dto/{request,response},entity,repository,service}` 형태의 기능별(package-by-feature) 구조입니다. `settlement`에는 `batch` 패키지가 추가로 있습니다(Spring Batch 의존성 보유 — 정산/지급 처리는 배치 잡으로 돌릴 것으로 예상). `global/`은 모든 도메인이 공유하는 공통 관심사를 담당합니다.
- **API 성공 응답 래퍼**: 컨트롤러는 성공 응답을 `global/response/ResultResponse<T>`로 감싸서 반환하고, `global/response/ResultCode`(비즈니스 성공 케이스별 `HttpStatus` + 한글 메시지 enum, 예: `USER_LOGIN_SUCCESS`)와 짝을 이룹니다. 새로운 성공 케이스는 임시 응답 바디를 만들지 말고 `ResultCode`에 enum 상수를 추가하세요.
- **예외 처리**: `global/exception/GlobalExceptionHandler`(`@RestControllerAdvice`)가 예외를 HTTP 응답으로 변환하는 유일한 지점입니다. 비즈니스 규칙 위반은 일반 예외 대신 `BusinessException(ErrorCode.X)`를 던지세요. `ErrorCode`는 실패 케이스별 `HttpStatus` + 한글 메시지 enum(`ResultCode`의 실패 버전)입니다. `@Valid`/`@NotBlank` 검증 실패, 메서드 인자 타입 불일치도 여기서 공통 처리되므로 컨트롤러별로 try/catch를 추가하지 마세요.
- **인증(JWT, Stateless)**: 서버 세션을 쓰지 않으며(`SessionCreationPolicy.STATELESS`), `SecurityConfig`에서 CSRF·폼 로그인·HTTP Basic을 모두 비활성화했습니다.
  - `global/auth/filter/LoginFilter` — `POST /login`용 커스텀 `AbstractAuthenticationProcessingFilter`로, 폼 파라미터가 아닌 JSON 바디를 읽어 `UsernamePasswordAuthenticationToken`을 만들고 `AuthenticationManager` → (아직 미구현) member 도메인의 `UserDetailsService`로 위임합니다.
  - `global/auth/filter/JwtFilter` — `Authorization: Bearer <token>` 헤더를 읽어 `JwtUtil`로 검증하고, `SecurityContextHolder`에 `CustomUserPrincipal`을 채웁니다.
  - `global/auth/jwt/util/JwtUtil` — JWT 생성/파싱(`jjwt`, HMAC 서명). 토큰에는 `email`, `role`, `type`(`JwtTokenType.ACCESS`/`REFRESH`) 클레임이 들어가며, Access/Refresh는 별도 서명 키가 아니라 이 `type` 클레임으로 구분합니다.
  - Refresh Token은 Redis 화이트리스트로 서버 측에 저장됩니다(`RedisRefreshTokenRepository`, 키 패턴 `refresh:user:{userId}`, TTL 1일). 토큰 재발급(`JwtService.reissueTokens`)은 서명/만료뿐 아니라 Redis에 저장된 값과 일치하는지도 검증하므로, 만료 전에도 로그아웃(`CustomLogoutHandler`)이나 강제 무효화가 가능합니다.
  - Refresh Token은 HttpOnly 쿠키(`JwtService.createCookie`)로, Access Token은 JSON 응답 바디/`Authorization` 헤더로 전달됩니다(쿠키 아님).
  - **아직 연결 안 됨**: `SecurityConfig.securityFilterChain`에는 CORS, 커스텀 로그아웃 핸들러, `JwtFilter`/`LoginFilter`를 추가해야 할 위치에 주석만 있고, 현재는 커스텀 필터 등록 없이 모든 요청에 `permitAll()`만 적용되어 있습니다. 보안 관련 작업 시 이미 연결되어 있다고 가정하지 말고 직접 등록하세요.
  - CORS는 `global/config/CorsMvcConfig`에 로컬 Vite/React 프론트엔드(`http://localhost:5173`) 기준으로 미리 설정되어 있습니다(credentials 허용).
- **Member 엔티티** (`domain/member/entity/Member.java`, `MemberRoleType.java`): DB 테이블 `member` 매핑.
  - 컬럼: `member_id`(PK), `member_email`(unique), `member_pw`, `member_nickname`, `member_role`(`MemberRoleType`: `MEMBER`/`HOST`/`ADMIN`, 기본값 `MEMBER`), `member_created_at`/`member_updated_at`(JPA Auditing `@CreatedDate`/`@LastModifiedDate`로 자동 관리 — `JpaAuditingConfig`의 `@EnableJpaAuditing` 필요), `member_deleted_at`(소프트 삭제, null이 아니면 탈퇴 처리).
  - 주최자(판매자) 정산 지급용 필드: `host_biz_number`, `host_biz_name`, `host_biz_bank`, `host_biz_depositor`, `host_biz_bank_number` — `HOST` 역할 회원의 정산금 입금 계좌 정보이며, `settlement` 도메인에서 사용될 예정입니다.
  - `CustomUserDetails`는 `Member`를 받아 `UserDetails`를 구현하며, `getUsername()`은 `member_email`을 반환하고, `isEnabled()`는 `member_deleted_at`이 없을 때만 `true`를 반환합니다(계정 잠금 필드는 없으며 소프트 삭제로 로그인 차단을 대신함).
- **SettlementEntry 엔티티**: `entry_created_at`(최초 생성 시각, 원래 정산 기간 판단 기준)과 `entry_updated_at`(`@LastModifiedDate`, 배치가 조정 대상 여부를 판단하는 기준) 둘 다 보유. 상태 변경 시 이 컬럼이 자동 갱신되므로, 일간 배치는 상태이력 테이블이 아니라 이 컬럼으로 변경 감지를 수행합니다.
- **영속성**: JPA(Hibernate, `ddl-auto: update`)로 엔티티를 관리하고, 손으로 짠 SQL이 필요한 경우(정산 리포트/집계 쿼리 등에 쓰일 것으로 예상) MyBatis(`classpath:mapper/**/*.xml`, 아직 미작성)를 병행합니다. 테스트 의존성으로 H2 + Testcontainers(Postgres)가 있지만, 아직 `TickleApplicationTests.contextLoads` 외에 실제로 사용하는 테스트는 없습니다.
- **엑셀 내보내기**: Apache POI 의존성 보유 — 정산 리포트 생성용입니다.
- **관측성**: Actuator + Micrometer/Prometheus(`/actuator/health`, `/actuator/prometheus` 등), JaCoCo 커버리지가 설정되어 있습니다. Swagger/OpenAPI UI는 `springdoc`으로 `/api-docs`에 구성되어 있습니다.

## Settlement 도메인 설계 원칙

- 정산 항목(건별 정산)은 예매(판매)가 생성되는 즉시 함께 생성됩니다 — 이후에 배치 잡이 판매 기록을 스캔해서 만드는 방식이 아닙니다.
- 예매(판매) 상태 변경 시: 정산 항목 자체의 현재 상태를 업데이트함과 **동시에**, 별도의 append-only 상태 이력 테이블에 (이전 상태 → 새 상태, 사유, 타임스탬프) 행을 같은 트랜잭션 안에서 삽입합니다. 이력 행은 절대 수정하지 않습니다.
- 일간/월간 집계는 마감(close)되고 나면 불변입니다. 마감 이후 발생한 취소나 변경 사항은 마감된 행을 업데이트하는 방식으로 절대 반영하지 않습니다 — `entry_type = ADJUSTMENT`인 새 행으로 기록하며, 원래 정산 기간으로 소급(backdate)하지 않고 조정이 배치에 의해 반영되는 시점(배치 실행일)의 날짜로 기록합니다. 구체적인 집계 계층 구조와 배치 실행 방식은 아래 "Settlement 집계 계층 구조" 참고.
- `contract_charge`(수수료율)는 정산 항목 생성 시점에 스냅샷으로 저장합니다. 호스트별로 협상하는 계약 요율이나 그 이력을 관리하는 별도 테이블(`contract`)은 없으며 — 전 판매자에게 동일하게 적용되는 `application.yaml`의 `host.contract.charge-rate` 고정값(현재 0.05)을 그대로 복사해 저장합니다. 그럼에도 매번 이 설정값을 조인/참조하지 않고 스냅샷으로 남기는 이유는, 이 값이 나중에 바뀌더라도 과거 정산 건은 판매 당시의 요율을 그대로 유지해야 하기 때문입니다.
- 주간(weekly) 정산 단계는 없습니다 — 일간/월간만 존재합니다(원래 팀 프로젝트는 일간/주간/월간이었으나 이 프로젝트는 다르게 갑니다).

## 도메인 상태 값

### 예매 상태 (Reservation status)

- `RESERVED`: 결제 완료
- `CANCELED`: 취소 — 취소(환불) 가능 기간은 공연일 전날 23:59:59까지이며, 이 시점 이후로는 예매를 취소할 수 없습니다.

### 건별 정산 상태 (Settlement entry status)

- `WAITING`: 판매 발생 시 기본 상태
- `CANCELED`: 취소 가능 기간 내 예매가 취소된 경우
- `COMPLETED`: 공연일 + 3일 시점에 WAITING → COMPLETED로 전환(배치)
- 참고: 이전 설계에서는 WAITING과 COMPLETED 사이에 지급 처리 단계용 READY 상태를
  별도로 뒀으나, 실제 지급(payout) API를 구현하지 않기로 하여 COMPLETED
  하나로 합쳤습니다.

전이 규칙: `WAITING` → (`CANCELED` | `COMPLETED`). `COMPLETED`나 `CANCELED` 이후에도 예매(reservation) 상태가 바뀌면 건별 정산의 현재 상태는 그대로 UPDATE되고, 변경 이력은 상태이력 테이블에 INSERT됩니다(둘 다 같은 트랜잭션). 다만 이미 마감되어 일간/월간 집계에 반영된 분량은 소급 수정하지 않고, 아래 "Settlement 집계 계층 구조"의 조정
(adjustment) 메커니즘으로 반영합니다.

## Settlement 집계 계층 구조

집계는 건별 → 일간 → 월간 순서로 쌓입니다. 월간은 건별에서 직접 계산하지 않고 반드시 일간 집계 결과를 합산해서 만듭니다.

**배치 스케줄(고정)**: 일간 배치는 매일 00:05, 월간 배치는 매월 1일 00:10에 실행됩니다. 전달 마지막 날(예: 7/31)의 일간 `NORMAL`은 다음 달 1일 00:05 일간 배치가 만들며, 월간 배치(00:10)는 반드시 그 뒤에 시작해야 합니다 — 순서가 뒤바뀌거나 일간 배치가 지연/실패하면 전달 마지막 날치가 월간 집계에서 누락됩니다.

**집계 엔티티 필드(설계 컨벤션)**:
- 일간 정산: `settlement_date`(집계 대상 원래 일자 = 건별 `entry_created_at`의 날짜 — `ADJUSTMENT` 행도 소급 없이 이 값을 그대로 유지), `settlement_daily_created_at`(배치가 실제로 실행된 시각), `entry_type`(`NORMAL`/`ADJUSTMENT`)
- 월간 정산: `settlement_year`/`settlement_month`(배치 실행월이 아니라 일간 `settlement_date`의 연/월을 그대로 참고), `entry_type`(`NORMAL`/`ADJUSTMENT`)

### 건별 → 일간 집계 (매일 00:05)

1. **신규 집계**: 상태가 `WAITING`인 어제(`entry_created_at` 기준)자 건별 정산 항목들을 합산해 일간 `NORMAL` 행을 생성합니다. `settlement_date`는 어제 날짜.
2. **조정 감지**: `settlement_entry.entry_updated_at`(JPA Auditing `@LastModifiedDate`)이 마지막 배치 실행 시각 이후로 갱신된 건들을 조회합니다. 그중 원래 날짜(`entry_created_at` 기준)에 이미 일간 `NORMAL` 행이 존재하는 것만 걸러내어(=이미 마감된 걸 건드린 것) 일간 `ADJUSTMENT` 행을 생성합니다. `settlement_date`는 원래 일자 유지(소급 없음), `settlement_daily_created_at`은 오늘 배치 시각.
   상태이력 테이블을 스캔하지 않고 `entry_updated_at`만으로 판단합니다 — 상태이력 테이블은 감사(audit) 목적으로만 유지되며, 배치의 변경 감지 로직에는 관여하지 않습니다.
   - **월간 전파(이벤트성 체크)**: 방금 만든 일간 `ADJUSTMENT` 각각에 대해, 그 `settlement_date`가 속한 연-월에 이미 월간 `NORMAL`이 존재하는지 즉시 확인합니다. 존재하면(=이미 마감된 달이면) 같은 배치 실행 안에서 월간 `ADJUSTMENT` 행도 함께 생성합니다. 별도의 "지난 실행 이후 변경분 스캔"이 아니라 방금 만든 ADJUSTMENT 각각에 대한 즉시 존재 여부 체크입니다. 월간이 아직 마감 전이면 여기서는 아무 것도 만들지 않고, 다가올 정기 월간 배치의 합산에 자연스럽게 포함됩니다.

일간 `NORMAL` 행은 한 번 생성되면 그 자체를 다시 UPDATE하지 않습니다. 이후 변경은 전부 `ADJUSTMENT` 행으로 별도 기록됩니다.

### 일간 → 월간 집계 (매월 1일 00:10)

지난달의 일간 `NORMAL` + `ADJUSTMENT` 행을 전부 합산해 월간 `NORMAL` 행을 생성합니다 — 하는 일은 "마감" 하나뿐이며, 별도의 "매일 도는 월간 배치"는 없습니다. 월간에 대한 조정 전파는 이 배치가 아니라 일간 배치가(위 2단계에서) 담당합니다.

### 조정이 필요한지 판단하는 기준

핵심은 **취소가 발생한 시각 자체가 아니라, 그 변경을 일간 배치가 언제 감지하는지**입니다. 일간 배치는 "마지막 배치 실행 시각 이후" 갱신분만 스캔하므로, 취소가 그날 배치에 걸릴 수도, 하루 늦게 걸릴 수도 있습니다 — 그리고 그 하루 차이가 월간 마감(00:10) 이전이냐 이후냐에 따라 월간 `ADJUSTMENT` 발생 여부가 갈립니다.

- 취소가 원래 일자의 일간 마감(다음날 00:05) **이전**에 일어나면: 애초에 `WAITING`으로 집계되지 않으므로 조정 자체가 불필요합니다 — 취소분이 빠진 채로 곧바로 정상 `NORMAL`에 집계됩니다.
- 취소가 일간 마감 **이후**에 일어나면: 다음 일간 배치(그다음 날 00:05)가 이를 감지해 일간 `ADJUSTMENT`를 생성합니다. 이 감지 시점에 해당 연-월의 월간 `NORMAL`이 아직 없으면(=월간 마감 전) 월간 조정은 불필요합니다 — 다가올 정기 월간 배치가 알아서 합산합니다. 반대로 이미 월간 `NORMAL`이 존재하면(=월간 마감 후) 같은 배치 실행에서 월간 `ADJUSTMENT`도 함께 생성됩니다.

**주의(감지 지연 케이스)**: 월간 마감은 매월 1일 00:10이고, 그날의 일간 배치는 그보다 5분 이른 00:05에 돕니다. 그래서 "그날 00:05 배치가 잡아내지 못하고 하루 넘어가 버린 취소"는 월간 마감을 이미 지나쳐버려 월간 `ADJUSTMENT`까지 필요해지고, "그날 00:05 배치가 마침 잡아낸 취소"는 5분 뒤 월간 마감에 자연스럽게 합산되어 월간 `ADJUSTMENT`가 필요 없습니다.
예: 7월 30일 판매 건이 8월 1일 00:05:01~23:59:59 사이에 취소되면, 그날(8/1) 00:05 배치는 이미 지나간 뒤라 다음 날(8/2) 00:05 배치가 되어서야 감지되고, 그때는 7월 월간이 이미 8/1 00:10에 `NORMAL`로 마감된 뒤이므로 일간 `ADJUSTMENT`와 7월 월간 `ADJUSTMENT`가 함께 생성됩니다. 반면 같은 건이 8월 1일 00:00:01~00:04:59 사이에 취소되면 그날 00:05 배치가 바로 감지해 일간 `ADJUSTMENT`를 만들고, 5분 뒤 00:10 월간 마감이 이를 포함해 정상 `NORMAL`로 집계하므로 월간 `ADJUSTMENT`는 발생하지 않습니다.

## 명시적으로 범위 밖(요청 없이는 추가하지 말 것)

- OAuth2/소셜 로그인 — 의도적으로 제외되어 있습니다. 이메일 + 비밀번호 + JWT만 사용합니다.
- 회원가입 시 이메일 인증 — 의도적으로 제외되어 있습니다.
- 실제 지급(payout) API 연동 — 정산은 상태 전이만 추적합니다(`WAITING → COMPLETED / CANCELED`). 실제 은행 송금 API 호출은 없습니다.

## 테스트

- 단위 테스트: JUnit5 + Mockito
- 통합 테스트: Testcontainers(Postgres) — 정산 계산 관련 로직은 H2보다 이 방식을 우선하세요. 숫자/날짜 처리가 운영 환경의 Postgres와 정확히 일치해야 하기 때문입니다.