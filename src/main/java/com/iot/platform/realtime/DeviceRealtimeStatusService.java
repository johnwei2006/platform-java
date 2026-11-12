package com.iot.platform.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.config.RealtimeProperties;
import com.iot.platform.model.DeviceRealtimeStatus;
import com.iot.platform.mq.UplinkMessage;
import com.iot.platform.service.converter.JsonNodeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 上行链路上的实时状态旁路：每收到一条上行就刷新一次在线快照与最新业务数据。
 *
 * <p>与 MongoDB 持久化、设备在线刷新、告警、业务转换相互独立；本服务吞掉所有异常，
 * 缓存不可用时只限频告警，绝不触发 RabbitMQ 重试或死信。
 */
@Service
public class DeviceRealtimeStatusService {

    private static final Logger log = LoggerFactory.getLogger(DeviceRealtimeStatusService.class);
    private static final long WARN_INTERVAL_MS = 60000L;
    private static final String ONLINE = "ONLINE";

    private final RedisDeviceStatusStore store;
    private final RealtimeProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicLong lastWarnAt = new AtomicLong(0L);

    @Autowired
    public DeviceRealtimeStatusService(RedisDeviceStatusStore store,
                                       RealtimeProperties properties,
                                       ObjectMapper objectMapper) {
        this.store = store;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void refresh(UplinkMessage message) {
        if (!properties.isEnabled() || message == null) {
            return;
        }
        String deviceId = JsonNodeUtils.text(message.getDeviceId());
        if (deviceId.isEmpty()) {
            return;
        }
        try {
            store.write(build(message, deviceId));
        } catch (Exception e) {
            long now = System.currentTimeMillis();
            long last = lastWarnAt.get();
            if (now - last >= WARN_INTERVAL_MS && lastWarnAt.compareAndSet(last, now)) {
                log.warn("realtime status refresh failed (degraded, uplink unaffected): deviceId={} reason={}",
                        deviceId, e.getMessage());
            }
        }
    }

    /** 从上行信封构造缓存快照；包级可见以便单测直接断言字段映射。 */
    @SuppressWarnings("unchecked")
    DeviceRealtimeStatus build(UplinkMessage message, String deviceId) {
        DeviceRealtimeStatus status = new DeviceRealtimeStatus();
        long now = System.currentTimeMillis();
        status.setDeviceId(deviceId);
        status.setStatus(ONLINE);
        status.setLastSeen(now);
        status.setUpdatedAt(now);
        status.setTtlSeconds((int) Math.min(
                properties.getTtlSeconds() > 0 ? properties.getTtlSeconds() : 1L,
                Integer.MAX_VALUE));
        status.setServiceType(message.getServiceType());
        status.setProtocolName(message.getProtocolName());
        status.setTransactionType(message.getTransactionType());
        status.setDecodeStatus(message.getDecodeStatus());
        status.setEventId(message.getEventId());
        status.setDeviceTimestamp(message.getTimestamp());
        status.setNodeId(JsonNodeUtils.text(message.getNodeId()));

        JsonNode decoded = message.getServiceDataDecoded();
        if (decoded == null || !decoded.isObject()) {
            return status;
        }
        status.setLatestBusinessData(objectMapper.convertValue(decoded, Map.class));
        String decodedNodeId = JsonNodeUtils.text(decoded.get("nodeId"));
        if (!decodedNodeId.isEmpty()) {
            status.setNodeId(decodedNodeId);
        }
        List<DeviceRealtimeStatus.SensorSnapshot> sensors = extractSensors(decoded.get("sensors"));
        if (!sensors.isEmpty()) {
            status.setSensors(sensors);
        }
        return status;
    }

    /** 只保留有效通道读数，规则与时序归档一致：channel 有效、channelResult==0、数值有限。 */
    private List<DeviceRealtimeStatus.SensorSnapshot> extractSensors(JsonNode array) {
        List<DeviceRealtimeStatus.SensorSnapshot> result =
                new ArrayList<DeviceRealtimeStatus.SensorSnapshot>();
        if (array == null || !array.isArray()) {
            return result;
        }
        for (JsonNode sensor : array) {
            if (sensor == null || !sensor.isObject()) {
                continue;
            }
            int channel = sensor.path("channel").asInt(-1);
            if (channel < 0 || sensor.path("channelResult").asInt(0) != 0) {
                continue;
            }
            double value = sensor.path("sensorValue").asDouble(Double.NaN);
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                continue;
            }
            DeviceRealtimeStatus.SensorSnapshot snapshot =
                    new DeviceRealtimeStatus.SensorSnapshot();
            snapshot.setChannel(channel);
            snapshot.setSensorValue(value);
            snapshot.setSensorCode(JsonNodeUtils.text(sensor.get("sensorCode")));
            snapshot.setSensorType(JsonNodeUtils.text(sensor.get("sensorType")));
            snapshot.setUnit(JsonNodeUtils.text(sensor.get("unit")));
            result.add(snapshot);
        }
        return result;
    }
}
