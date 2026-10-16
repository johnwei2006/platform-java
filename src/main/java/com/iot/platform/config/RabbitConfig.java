package com.iot.platform.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 声明后台所需的 MQ 资源：
 * - platform.uplink：消费来自 dtm 的上行数据
 * - platform.downlink：发布下行指令给 dtm
 */
@Configuration
public class RabbitConfig {

    @Value("${app.mq.exchange}")
    private String exchange;
    @Value("${app.mq.platform-uplink}")
    private String platformUplink;
    @Value("${app.mq.platform-downlink}")
    private String platformDownlink;

    @Bean
    public TopicExchange iotExchange() {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange("iot.dlx", true, false);
    }

    private Queue durableWithDlq(String name) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", "iot.dlx")
                .withArgument("x-dead-letter-routing-key", "iot.dlq")
                .build();
    }

    @Bean
    public Queue platformUplinkQueue() {
        return durableWithDlq(platformUplink);
    }

    @Bean
    public Queue platformDownlinkQueue() {
        return durableWithDlq(platformDownlink);
    }

    @Bean
    public Queue deadLetterQueue() {
        return new Queue("iot.dlq", true);
    }

    @Bean
    public Binding bindPlatformUplink() {
        return new Binding(platformUplink, Binding.DestinationType.QUEUE, exchange, platformUplink, null);
    }

    @Bean
    public Binding bindPlatformDownlink() {
        return new Binding(platformDownlink, Binding.DestinationType.QUEUE, exchange, platformDownlink, null);
    }

    @Bean
    public Binding bindDeadLetter() {
        return new Binding("iot.dlq", Binding.DestinationType.QUEUE, "iot.dlx", "iot.dlq", null);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        return new RabbitTemplate(connectionFactory);
    }
}
