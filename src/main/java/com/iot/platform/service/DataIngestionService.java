package com.iot.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.model.Alarm;
import com.iot.platform.model.DataRecord;
import com.iot.platform.model.Device;
import com.iot.platform.mq.UplinkMessage;
import com.iot.platform.repository.AlarmRepository;
import com.iot.platform.repository.DataRecordRepository;
import com.iot.platform.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 上行数据接入：消费 platform.uplink，持久化数据、更新设备在线状态，
 * 并按简单规则生成告警。
 */
@Service
public class DataIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DataIngestionService.class);

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DataRecordRepository dataRecordRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private AlarmRepository alarmRepository;

    @RabbitListener(queues = "${app.mq.platform-uplink}")
    public void onUplink(Message message) {
        try {
            UplinkMessage up = objectMapper.readValue(message.getBody(), UplinkMessage.class);

            DataRecord rec = new DataRecord();
            rec.setDeviceId(up.getDeviceId());
            rec.setServiceType(up.getServiceType());
            rec.setServiceVersion(up.getServiceVersion());
            rec.setServiceData(up.getServiceData());
            rec.setEventId(up.getEventId());
            rec.setCreatedAt(new Date());
            dataRecordRepository.save(rec);

            Device d = deviceRepository.findByDeviceId(up.getDeviceId()).orElseGet(() -> {
                Device n = new Device();
                n.setDeviceId(up.getDeviceId());
                n.setCreatedAt(new Date());
                return n;
            });
            d.setStatus("ONLINE");
            d.setLastSeen(new Date());
            d.setUpdatedAt(new Date());
            deviceRepository.save(d);

            // 简单规则：serviceType == 99 视为设备主动告警
            if (up.getServiceType() == 99) {
                Alarm a = new Alarm();
                a.setDeviceId(up.getDeviceId());
                a.setRule("SERVICE_TYPE_99");
                a.setLevel("ERROR");
                a.setMessage("device reported alarm serviceType=99");
                a.setStatus("OPEN");
                a.setCreatedAt(new Date());
                alarmRepository.save(a);
            }

            log.info("ingested uplink from {} serviceType={}", up.getDeviceId(), up.getServiceType());
        } catch (Exception e) {
            log.error("failed to ingest uplink", e);
            throw new RuntimeException(e); // 触发重试 / 死信
        }
    }
}
