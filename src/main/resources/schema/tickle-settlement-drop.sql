-- ================================================================
-- tickle-settlement-schema.sql로 생성된 테이블을 전부 삭제하는 스크립트.
--
-- application.yaml의 spring.sql.init.schema-locations/data-locations에
-- 등록되어 있지 않으므로 앱 기동 시 자동 실행되지 않는다. 필요할 때 psql 등으로 수동 실행한다.
--
-- 순서는 FK 참조 방향의 역순(자식 -> 부모)이다. CASCADE에 기대지 않고 순서 자체로 안전하게
-- 지워지도록 구성했다. 참조하는 쪽을 먼저 지워야 참조당하는 쪽을 지울 때 막히지 않는다.
--
-- 의존 관계 요약:
--   status                     <- performance, reservation, settlement_entry, settlement_status_history
--   member                     <- performance, reservation, settlement_entry, settlement_daily, settlement_monthly
--   performance                <- reservation, settlement_entry, settlement_daily, settlement_monthly
--   reservation                <- settlement_entry
--   settlement_entry           <- settlement_status_history
--   settlement_daily, settlement_monthly, batch_metadata : 다른 테이블이 참조하지 않음
-- ================================================================

DROP TABLE IF EXISTS settlement_status_history;
DROP TABLE IF EXISTS settlement_daily;
DROP TABLE IF EXISTS settlement_monthly;
DROP TABLE IF EXISTS settlement_entry;
DROP TABLE IF EXISTS reservation;
DROP TABLE IF EXISTS performance;
DROP TABLE IF EXISTS batch_metadata;
DROP TABLE IF EXISTS member;
DROP TABLE IF EXISTS status;
