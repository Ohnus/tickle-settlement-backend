package com.settlement.tickle.domain.settlement.batch.chunk.cursor;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

// JdbcCursorItemReader가 커서에서 한 행을 가져올 때마다 이 RowMapper가 호출된다
// 즉 이 mapRow() 호출 횟수가 곧 row 하나당 객체 하나씩 생기는 청크 모델의 특징 그 자체다.
public class SettlementEntryRowMapper implements RowMapper<SettlementEntryRow> {

    @Override
    public SettlementEntryRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new SettlementEntryRow(
                rs.getLong("member_id"),
                rs.getLong("performance_id"),
                rs.getString("performance_title"),
                rs.getLong("sales_amount"),
                rs.getLong("refund_amount"),
                rs.getLong("gross_amount"),
                rs.getLong("commission"),
                rs.getLong("net_amount")
        );
    }
}
