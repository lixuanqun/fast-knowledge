package com.fast.knowledge.config;

import com.baomidou.mybatisplus.annotation.DbType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MybatisPlusConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveDbType_detectsSqlite() throws Exception {
        String url = "jdbc:sqlite:" + tempDir.resolve("test.db");
        try (var conn = DriverManager.getConnection(url);
             var ds = new org.springframework.jdbc.datasource.SingleConnectionDataSource(conn, true)) {
            assertEquals(DbType.SQLITE, MybatisPlusConfig.resolveDbType(ds));
        }
    }
}
