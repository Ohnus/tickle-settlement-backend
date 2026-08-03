-- ================================================================
-- 1. 기본 참조 테이블
-- ================================================================
CREATE TABLE IF NOT EXISTS status (
                        status_id SERIAL PRIMARY KEY,
                        status_code INTEGER NOT NULL,
                        status_created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        status_description VARCHAR(20) NOT NULL,
                        status_type VARCHAR(20) NOT NULL,   -- 예: 'MEMBER', 'PERFORMANCE', 'RESERVATION', 'SETTLEMENT'

                        CONSTRAINT check_status_type CHECK (status_type IN ('MEMBER', 'PERFORMANCE', 'RESERVATION', 'SETTLEMENT'))
);

-- ================================================================
-- 2. 회원 (판매자/구매자 공용)
-- ================================================================
CREATE TABLE IF NOT EXISTS member (
                        member_id BIGSERIAL PRIMARY KEY,
                        member_email VARCHAR(30) NOT NULL UNIQUE,
                        member_pw VARCHAR(255) NOT NULL,
                        member_nickname VARCHAR(10) NOT NULL,
                        member_role VARCHAR(20) NOT NULL DEFAULT 'HOST',  -- 정산 플랫폼 특성상 대부분 가입자가 판매자라 기본값 HOST
                        member_created_at TIMESTAMPTZ DEFAULT NOW(),
                        member_updated_at TIMESTAMPTZ DEFAULT NOW(),
                        member_deleted_at TIMESTAMPTZ,

    -- 주최자(판매자) 정산 지급 관련 필드
                        host_biz_number VARCHAR(15),
                        host_biz_name VARCHAR(15),
                        host_biz_bank VARCHAR(10),
                        host_biz_depositor VARCHAR(10),
                        host_biz_bank_number VARCHAR(25),

                        CONSTRAINT check_member_role CHECK (member_role IN ('MEMBER', 'HOST', 'ADMIN'))
);

-- ================================================================
-- 3. 공연 도메인 (더미 데이터 최소 구성)
-- ================================================================
CREATE TABLE IF NOT EXISTS performance (
                             performance_id BIGSERIAL PRIMARY KEY,
                             member_id BIGINT NOT NULL REFERENCES member(member_id),  -- 주최자(판매자)
                             status_id INTEGER NOT NULL REFERENCES status(status_id),
                             performance_title VARCHAR(50) NOT NULL,
                             performance_price INTEGER NOT NULL,          -- 예매 시 가격 참조용 (좌석 등급 없이 단일가로 단순화)
                             performance_start_date TIMESTAMPTZ NOT NULL,
                             performance_end_date TIMESTAMPTZ NOT NULL,   -- WAITING → COMPLETED 전환 기준(공연일+3일)에 사용
                             performance_reservation_start_date TIMESTAMPTZ NOT NULL,
                             performance_reservation_end_date TIMESTAMPTZ NOT NULL,   -- 예매 취소 가능 기한(이 시각까지)에 사용
                             performance_created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ================================================================
-- 4. 예매(판매) 도메인
-- ================================================================
CREATE TABLE IF NOT EXISTS reservation (
                             reservation_id BIGSERIAL PRIMARY KEY,
                             member_id BIGINT NOT NULL REFERENCES member(member_id),           -- 구매자
                             performance_id BIGINT NOT NULL REFERENCES performance(performance_id),
                             status_id INTEGER NOT NULL REFERENCES status(status_id),          -- 예매 상태 (결제완료/취소 등)
                             reservation_code VARCHAR(30) NOT NULL,
                             reservation_price INTEGER NOT NULL,
                             reservation_created_at TIMESTAMPTZ DEFAULT NOW(),
                             reservation_updated_at TIMESTAMPTZ
);

-- ================================================================
-- 5. 정산 - 건별 (판매와 동시에 생성되는 원장)
-- ================================================================
CREATE TABLE IF NOT EXISTS settlement_entry (
                                  settlement_entry_id BIGSERIAL PRIMARY KEY,
                                  reservation_id BIGINT NOT NULL UNIQUE REFERENCES reservation(reservation_id),
                                  member_id BIGINT NOT NULL REFERENCES member(member_id),   -- 정산 대상 판매자(호스트)
                                  status_id INTEGER NOT NULL REFERENCES status(status_id),  -- 현재 상태: WAITING/COMPLETED/CANCELED
                                  performance_id BIGINT NOT NULL REFERENCES performance(performance_id),  -- 집계/식별 기준(제목 텍스트 아님)
                                  performance_title VARCHAR(50) NOT NULL,        -- 반정규화 (조회 시 조인 회피, 표시용)
                                  performance_end_date TIMESTAMPTZ NOT NULL,      -- 상태 전이 스케줄링 기준
                                  sales_amount BIGINT NOT NULL,
                                  refund_amount BIGINT NOT NULL DEFAULT 0,
                                  gross_amount BIGINT NOT NULL,
                                  contract_charge NUMERIC(4,3) NOT NULL,          -- 정산 시점 수수료율 스냅샷 (현재는 host.contract.charge-rate 고정값 0.05)
                                  commission BIGINT NOT NULL,
                                  net_amount BIGINT NOT NULL,
                                  entry_created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                  entry_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()  -- 상태 변경 시 갱신 (JPA @LastModifiedDate) — 일간 배치의 조정 대상 판별 기준
);

-- 조회를 위한 인덱스(성능용, 무결성과 무관): 일간 배치가 "지난 실행 이후 변경된 건"을 조회할 때 이 컬럼으로 필터링한다.
-- 있고 없고 차이를 직접 비교해보기 위해 우선 주석 처리.
-- CREATE INDEX IF NOT EXISTS idx_settlement_entry_updated_at
--     ON settlement_entry (entry_updated_at);

-- 정산 상태 변경 이력 (append-only, 감사 추적용)
CREATE TABLE IF NOT EXISTS settlement_status_history (
                                           settlement_status_history_id BIGSERIAL PRIMARY KEY,
                                           settlement_entry_id BIGINT NOT NULL REFERENCES settlement_entry(settlement_entry_id),
                                           previous_status_id INTEGER REFERENCES status(status_id),   -- 최초 생성 시 NULL 가능
                                           changed_status_id INTEGER NOT NULL REFERENCES status(status_id),
                                           change_reason VARCHAR(100),
                                           changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 조회를 위한 인덱스(성능용, 무결성과 무관): 정산 건 하나의 이력을 조회할 때 사용
-- (FK는 Postgres가 자동으로 인덱싱해주지 않음). 마찬가지로 우선 주석 처리.
-- CREATE INDEX IF NOT EXISTS idx_settlement_status_history_entry
--     ON settlement_status_history (settlement_entry_id);

-- ================================================================
-- 6. 정산 - 일별/월별 (배치 집계, 마감 후 불변 + 조정항목 방식)
-- ================================================================
CREATE TABLE IF NOT EXISTS settlement_daily (
                                  settlement_daily_id BIGSERIAL PRIMARY KEY,
                                  member_id BIGINT NOT NULL REFERENCES member(member_id),
                                  performance_id BIGINT NOT NULL REFERENCES performance(performance_id),  -- 집계/식별 기준(제목 텍스트 아님)
                                  performance_title VARCHAR(50) NOT NULL,        -- 반정규화 (조회 시 조인 회피, 표시용)
                                  settlement_date DATE NOT NULL,
                                  entry_type VARCHAR(10) NOT NULL DEFAULT 'NORMAL',  -- 'NORMAL' | 'ADJUSTMENT'

                                  sales_amount BIGINT NOT NULL,
                                  refund_amount BIGINT NOT NULL DEFAULT 0,
                                  gross_amount BIGINT NOT NULL,
                                  commission BIGINT NOT NULL,
                                  net_amount BIGINT NOT NULL,

                                  settlement_daily_created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                                  CONSTRAINT check_entry_type CHECK (entry_type IN ('NORMAL', 'ADJUSTMENT'))
);

-- 무결성 보장용 인덱스(성능용 아님, 항상 활성): 하루/판매자/공연당 정상 마감분은 단 하나만 (조정분은 여러 건 허용).
-- 부수 효과로 배치가 "이 날짜에 NORMAL이 이미 있는지" 조회할 때도 이 인덱스를 탄다.
CREATE UNIQUE INDEX IF NOT EXISTS uniq_settlement_daily_normal
    ON settlement_daily (member_id, performance_id, settlement_date)
    WHERE entry_type = 'NORMAL';

CREATE TABLE IF NOT EXISTS settlement_monthly (
                                    settlement_monthly_id BIGSERIAL PRIMARY KEY,
                                    member_id BIGINT NOT NULL REFERENCES member(member_id),
                                    performance_id BIGINT NOT NULL REFERENCES performance(performance_id),  -- 집계/식별 기준(제목 텍스트 아님)
                                    performance_title VARCHAR(50) NOT NULL,        -- 반정규화 (조회 시 조인 회피, 표시용)
                                    settlement_year INTEGER NOT NULL,
                                    settlement_month INTEGER NOT NULL,
                                    entry_type VARCHAR(10) NOT NULL DEFAULT 'NORMAL',

                                    sales_amount BIGINT NOT NULL,
                                    refund_amount BIGINT NOT NULL DEFAULT 0,
                                    gross_amount BIGINT NOT NULL,
                                    commission BIGINT NOT NULL,
                                    net_amount BIGINT NOT NULL,

                                    settlement_monthly_created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                                    CONSTRAINT check_entry_type_monthly CHECK (entry_type IN ('NORMAL', 'ADJUSTMENT'))
);

-- 무결성 보장용 인덱스(성능용 아님, 항상 활성): 위와 동일한 이유의 월간 버전
CREATE UNIQUE INDEX IF NOT EXISTS uniq_settlement_monthly_normal
    ON settlement_monthly (member_id, performance_id, settlement_year, settlement_month)
    WHERE entry_type = 'NORMAL';

-- ================================================================
-- 7. 배치 메타데이터 (증분 처리 기준점 관리)
-- ================================================================
CREATE TABLE IF NOT EXISTS batch_metadata (
                                job_name VARCHAR(100) NOT NULL,
                                last_processed_at TIMESTAMPTZ NOT NULL,
                                updated_at TIMESTAMPTZ NOT NULL,
                                CONSTRAINT batch_metadata_pkey PRIMARY KEY (job_name)
);
