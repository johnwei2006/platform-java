package com.iot.platform.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册实时状态配置属性。
 *
 * <p>StringRedisTemplate 由 Spring Boot 的 RedisAutoConfiguration 提供（Lettuce 懒连接），
 * 这里不额外创建连接工厂；未部署 Redis 时应用仍可启动。
 */
@Configuration
@EnableConfigurationProperties(RealtimeProperties.class)
public class RealtimeConfig {
}
