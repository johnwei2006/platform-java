package com.iot.platform.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.config.RealtimeConfig;
import org.junit.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.Assert.assertNotNull;

/**
 * 验证 Redis 相关 Bean 在「没有 Redis 服务」时仍能完成装配：Lettuce 是懒连接，
 * 应用启动阶段不会发起网络连接。只加载 Redis 配置，避免依赖 MongoDB/RabbitMQ 等外部服务。
 */
public class RealtimeStartupTest {

    @Test
    public void redisBeansStartWithoutRedisServer() {
        System.setProperty("spring.redis.host", "127.0.0.1");
        System.setProperty("spring.redis.port", "6399");
        System.setProperty("spring.redis.timeout", "500");
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        try {
            ctx.getBeanFactory().registerSingleton("objectMapper", new ObjectMapper());
            ctx.register(RedisAutoConfiguration.class, RealtimeConfig.class,
                    RedisDeviceStatusStore.class);
            ctx.refresh();

            assertNotNull(ctx.getBean(StringRedisTemplate.class));
            assertNotNull(ctx.getBean(RedisDeviceStatusStore.class));
            assertNotNull(ctx.getBean(com.iot.platform.config.RealtimeProperties.class));
        } finally {
            ctx.close();
            System.clearProperty("spring.redis.host");
            System.clearProperty("spring.redis.port");
            System.clearProperty("spring.redis.timeout");
        }
    }
}
