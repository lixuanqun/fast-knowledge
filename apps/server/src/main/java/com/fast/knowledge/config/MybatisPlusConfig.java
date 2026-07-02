package com.fast.knowledge.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Configuration
@MapperScan("com.fast.knowledge.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(DataSource dataSource) throws SQLException {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(resolveDbType(dataSource)));
        return interceptor;
    }

    static DbType resolveDbType(DataSource dataSource) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL().toLowerCase();
            if (url.contains("postgresql")) {
                return DbType.POSTGRE_SQL;
            }
            if (url.contains("sqlite")) {
                return DbType.SQLITE;
            }
            if (url.contains("mysql") || url.contains("mariadb")) {
                return DbType.MYSQL;
            }
        }
        return DbType.POSTGRE_SQL;
    }
}
