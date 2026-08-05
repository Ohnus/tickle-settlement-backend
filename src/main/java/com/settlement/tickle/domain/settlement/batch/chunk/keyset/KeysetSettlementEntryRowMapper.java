package com.settlement.tickle.domain.settlement.batch.chunk.keyset;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class KeysetSettlementEntryRowMapper implements RowMapper<KeysetSettlementEntryRow> {

    @Override
    public KeysetSettlementEntryRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new KeysetSettlementEntryRow(
                rs.getLong("settlement_entry_id"),
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
