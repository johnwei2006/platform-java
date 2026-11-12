package com.iot.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * TDengine 的 DataSource / JdbcTemplate 由这里手工创建。
 *
 * <p>刻意不引入 spring-boot-starter-jdbc：Spring Boot 2.1.6 的 JDBC 自动配置会在
 * classpath 出现 spring-jdbc 后尝试探测内嵌数据源，且与现有 MongoDB 自动配置无关。
 * 这里创建的 DriverManagerDataSource 是惰性的，构造时不建立任何连接，因此
 * 时序库未部署时应用与单元测试都能正常启动。
 */
@Configuration
@EnableConfigurationProperties(TdEngineProperties.class)
public class TdEngineConfig {

    private static final Logger log = LoggerFactory.getLogger(TdEngineConfig.class);

    @Bean
    public JdbcTemplate tdEngineJdbcTemplate(TdEngineProperties properties) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(buildJdbcUrl(properties));
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        log.info("TDengine JdbcTemplate initialized: host={} port={} database={} enabled={}",
                properties.getHost(), properties.getPort(), properties.getDatabase(),
                properties.isEnabled());
        return new JdbcTemplate(dataSource);
    }

    /**
     * 构造原生 JDBC URL；用户名/口令通过 DataSource 的 Properties 传递，不拼进 URL。
     */
    static String buildJdbcUrl(TdEngineProperties properties) {
        return "jdbc:TAOS://" + properties.getHost() + ":" + properties.getPort()
                + "/" + sanitizeIdentifier(properties.getDatabase())
                + "?charset=UTF-8&locale=en_US.UTF-8&timezone=UTC";
    }

    /**
     * 库名只允许字母数字下划线，避免配置项拼接进 DDL/SQL 时引入注入。
     */
    public static String sanitizeIdentifier(String raw) {
        if (raw == null) {
            return "iot";
        }
        String cleaned = raw.replaceAll("[^A-Za-z0-9_]", "");
        return cleaned.isEmpty() ? "iot" : cleaned;
    }
}
