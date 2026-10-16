package com.iot.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.mq.DownlinkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DownlinkService {

    private static final Logger log = LoggerFactory.getLogger(DownlinkService.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.mq.exchange}")
    private String exchange;
    @Value("${app.mq.platform-downlink}")
    private String routingKey;

    public void send(DownlinkMessage dm) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(dm);
            rabbitTemplate.send(exchange, routingKey, new Message(body, new MessageProperties()));
            log.info("sent downlink to device {}", dm.getDeviceId());
        } catch (Exception e) {
            log.error("failed to send downlink", e);
            throw new RuntimeException(e);
        }
    }
}
