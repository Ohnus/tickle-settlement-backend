-- ================================================================
-- reservation 도메인 개발/테스트에 필요한 최소 더미 데이터.
-- spring.sql.init.mode: always라 매 기동마다 실행되므로,
-- 전부 자연키 기준 존재 여부를 확인하는 INSERT ... SELECT ... WHERE NOT EXISTS 형태로 작성해 여러 번 실행해도 안전하다.
-- ================================================================

-- 1. 상태(status) 더미 — reservation/settlement_entry가 실제로 쓰는 값만.
INSERT INTO status (status_code, status_description, status_type)
SELECT 1, 'RESERVED', 'RESERVATION'
WHERE NOT EXISTS (SELECT 1 FROM status WHERE status_type = 'RESERVATION' AND status_description = 'RESERVED');

INSERT INTO status (status_code, status_description, status_type)
SELECT 2, 'CANCELED', 'RESERVATION'
WHERE NOT EXISTS (SELECT 1 FROM status WHERE status_type = 'RESERVATION' AND status_description = 'CANCELED');

INSERT INTO status (status_code, status_description, status_type)
SELECT 1, 'WAITING', 'SETTLEMENT'
WHERE NOT EXISTS (SELECT 1 FROM status WHERE status_type = 'SETTLEMENT' AND status_description = 'WAITING');

INSERT INTO status (status_code, status_description, status_type)
SELECT 2, 'CANCELED', 'SETTLEMENT'
WHERE NOT EXISTS (SELECT 1 FROM status WHERE status_type = 'SETTLEMENT' AND status_description = 'CANCELED');

INSERT INTO status (status_code, status_description, status_type)
SELECT 3, 'COMPLETED', 'SETTLEMENT'
WHERE NOT EXISTS (SELECT 1 FROM status WHERE status_type = 'SETTLEMENT' AND status_description = 'COMPLETED');

-- performance.status_id가 NOT NULL FK라 더미 공연을 만들려면 PERFORMANCE 타입 상태도 하나는 있어야 한다.
-- performance는 더미 용도로만 존재하는 도메인이라 이 값에 따른 분기 로직은 없다.
INSERT INTO status (status_code, status_description, status_type)
SELECT 1, 'ON_SALE', 'PERFORMANCE'
WHERE NOT EXISTS (SELECT 1 FROM status WHERE status_type = 'PERFORMANCE' AND status_description = 'ON_SALE');

-- 2. 더미 판매자(HOST) 1명 — 더미 공연의 주최자로 필요.
-- 비밀번호는 'password123!'을 BCrypt로 해시한 실제 값이라, Swagger에서 이 계정으로 로그인 테스트도 가능하다.
-- INSERT INTO member (member_email, member_pw, member_nickname, member_role,
--                      host_biz_number, host_biz_name, host_biz_bank, host_biz_depositor, host_biz_bank_number)
-- SELECT 'dummy-host@tickle.com',
--        '$2a$10$Fb6DZQUIkNijpH/UIVb/oOWA7AVvPDRUmA5rPCfehE5/JDYpyZ2M.',
--        '더미호스트', 'HOST',
--        '123-45-67890', '티클컴퍼니', '국민은행', '홍길동', '123456-78-901234'
-- WHERE NOT EXISTS (SELECT 1 FROM member WHERE member_email = 'dummy-host@tickle.com');

-- 3. 더미 공연 1건 — reservation이 참조할 대상.
-- 시작일을 기동 시점 기준 7일 뒤로 잡아서, "예매 종료일까지 취소 가능" 로직을 당분간 넉넉하게 테스트할 수 있게 한다.
-- 예매 종료일은 공연 시작일 하루 전으로 잡아 기존 "공연일 전날까지" 테스트 시나리오와 동일하게 동작하게 한다.
-- (주의: 이 값은 최초 생성 시점에 고정되고 이후로는 안 바뀐다. 개발 환경을 오래 리셋하지 않으면 언젠가 과거 날짜가 되어
-- 취소 테스트가 더 이상 안 될 수 있음. 그때는 DB에서 이 행을 지우고 다시 기동하면 된다.)
-- INSERT INTO performance (member_id, status_id, performance_title, performance_price,
--                           performance_start_date, performance_end_date,
--                           performance_reservation_start_date, performance_reservation_end_date)
-- SELECT m.member_id,
--        s.status_id,
--        '더미 공연 - 봄맞이 콘서트',
--        50000,
--        NOW() + INTERVAL '7 days',
--        NOW() + INTERVAL '7 days' + INTERVAL '3 hours',
--        NOW(),
--        NOW() + INTERVAL '6 days'
-- FROM member m, status s
-- WHERE m.member_email = 'dummy-host@tickle.com'
--   AND s.status_type = 'PERFORMANCE' AND s.status_description = 'ON_SALE'
--   AND NOT EXISTS (SELECT 1 FROM performance WHERE performance_title = '더미 공연 - 봄맞이 콘서트');
