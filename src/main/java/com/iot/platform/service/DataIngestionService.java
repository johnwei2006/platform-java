package com.iot.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.model.Alarm;
import com.iot.platform.model.DataRecord;
import com.iot.platform.model.Device;
import com.iot.platform.mq.UplinkMessage;
import com.iot.platform.repository.AlarmRepository;
import com.iot.platform.repository.DataRecordRepository;
import com.iot.platform.repository.DeviceRepository;
import com.iot.platform.realtime.DeviceRealtimeStatusService;
import com.iot.platform.service.converter.UplinkBusinessDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;

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
    @Autowired(required = false)
    private UplinkBusinessDispatcher uplinkBusinessDispatcher;
    @Autowired(required = false)
    private DeviceRealtimeStatusService realtimeStatusService;

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
            rec.setServiceDataDecoded(toMap(up.getServiceDataDecoded()));
            rec.setProtocolName(up.getProtocolName());
            rec.setTransactionType(up.getTransactionType());
            rec.setDecodeStatus(up.getDecodeStatus());
            rec.setDecodeError(up.getDecodeError());
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

            log.info("ingested uplink from {} serviceType={} protocol={} decodeStatus={} transactionType={}",
                    up.getDeviceId(), up.getServiceType(),
                    up.getProtocolName() == null ? "-" : up.getProtocolName(),
                    up.getDecodeStatus() == null ? "-" : up.getDecodeStatus(),
                    up.getTransactionType() == null ? "-" : up.getTransactionType());

            // 业务转换旁路（传感器时序归档等）：内部隔离异常，绝不改变上行 ACK 语义。
            try {
                if (uplinkBusinessDispatcher != null) {
                    uplinkBusinessDispatcher.dispatch(up);
                }
            } catch (Exception e) {
                log.warn("uplink business dispatch failed: deviceId={} serviceType={} reason={}",
                        up.getDeviceId(), up.getServiceType(), e.getMessage());
            }

            // 实时状态旁路（Redis 在线快照 + 最新业务数据）：与业务转换互不依赖，独立隔离异常。
            try {
                if (realtimeStatusService != null) {
                    realtimeStatusService.refresh(up);
                }
            } catch (Exception e) {
                log.warn("realtime status refresh failed: deviceId={} serviceType={} reason={}",
                        up.getDeviceId(), up.getServiceType(), e.getMessage());
            }
        } catch (Exception e) {
            log.error("failed to ingest uplink", e);
            throw new RuntimeException(e); // 触发重试 / 死信
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return objectMapper.convertValue(node, Map.class);
    }
}
