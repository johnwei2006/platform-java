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
        if (dm.getDeviceId() == null || dm.getDeviceId().trim().isEmpty()) {
            throw new IllegalArgumentException("deviceId is required for downlink");
        }
        boolean structured = dm.getServiceDataDecoded() != null && !dm.getServiceDataDecoded().isNull();
        if (!structured && (dm.getServiceData() == null || dm.getServiceData().isEmpty())) {
            // 空载荷协议也必须给出字符串（ProxyTcp 的 base64 适配器不接受 JSON null）
            dm.setServiceData("");
        }
        try {
            byte[] body = objectMapper.writeValueAsBytes(dm);
            MessageProperties props = new MessageProperties();
            props.setContentType("application/json");
            props.setContentEncoding("UTF-8");
            rabbitTemplate.send(exchange, routingKey, new Message(body, props));
            log.info("sent downlink to device {} serviceType={} structured={}",
                    dm.getDeviceId(), dm.getServiceType(), structured);
        } catch (Exception e) {
            log.error("failed to send downlink", e);
            throw new RuntimeException(e);
        }
    }
}
