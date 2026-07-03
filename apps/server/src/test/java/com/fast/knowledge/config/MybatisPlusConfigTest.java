package com.fast.knowledge.config;

import com.baomidou.mybatisplus.annotation.DbType;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MybatisPlusConfigTest {

    @Test
    void resolveDbType_detectsPostgresql() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getURL()).thenReturn("jdbc:postgresql://localhost:5432/fast_knowledge");

        assertEquals(DbType.POSTGRE_SQL, MybatisPlusConfig.resolveDbType(ds));
    }
}
