package com.fast.knowledge.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps PostgreSQL JSONB/JSON columns to Java String.
 * PostgreSQL JDBC returns JSONB as PGobject; this handler converts to plain String.
 */
public class JsonbToStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        PGobject pg = new PGobject();
        pg.setType("jsonb");
        pg.setValue(parameter);
        ps.setObject(i, pg);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toString(rs.getObject(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toString(rs.getObject(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toString(cs.getObject(columnIndex));
    }

    private static String toString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof PGobject pg) {
            return pg.getValue();
        }
        return value.toString();
    }
}
