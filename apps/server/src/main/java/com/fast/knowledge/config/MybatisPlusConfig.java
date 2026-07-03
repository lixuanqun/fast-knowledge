package com.fast.knowledge.config;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

@Configuration
@MapperScan("com.fast.knowledge.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(DataSource dataSource) throws SQLException {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(resolveDbType(dataSource)));
        return interceptor;
    }

    @Bean
    public ConfigurationCustomizer timestamptzTypeHandlerCustomizer() {
        return configuration -> configuration.getTypeHandlerRegistry()
                .register(LocalDateTime.class, TimestamptzToLocalDateTimeTypeHandler.class);
    }

    static DbType resolveDbType(DataSource dataSource) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL().toLowerCase();
            if (url.contains("postgresql")) {
                return DbType.POSTGRE_SQL;
            }
            if (url.contains("mysql") || url.contains("mariadb")) {
                return DbType.MYSQL;
            }
        }
        return DbType.POSTGRE_SQL;
    }
}
