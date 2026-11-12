package com.iot.platform.service.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.iot.platform.mq.UplinkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 传感器配置报文转换器：0X10 GATEWAY_CONFIG_SENSOR_REPORT(16)、0X11 GATEWAY_CONFIG_SENSOR(17)。
 *
 * <p>schema 的 sensors 数组含 channel / sensorType / sensorCode，这里登记为
 * (stationcode, nodecode, channel) -> (sensorCode, sensorType)，供 0X18 实时读数补全标签。
 */
@Component
public class SensorConfigConverter implements UplinkBusinessConverter {

    public static final int SERVICE_CONFIG_SENSOR_REPORT = 0x10;
    public static final int SERVICE_CONFIG_SENSOR = 0x11;

    private static final Logger log = LoggerFactory.getLogger(SensorConfigConverter.class);

    private final SensorMetadataRegistry registry;

    @Autowired
    public SensorConfigConverter(SensorMetadataRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean supports(int serviceType) {
        return serviceType == SERVICE_CONFIG_SENSOR_REPORT || serviceType == SERVICE_CONFIG_SENSOR;
    }

    @Override
    public ConversionResult convert(UplinkMessage message) {
        JsonNode decoded = message.getServiceDataDecoded();
        if (decoded == null || !decoded.isObject()) {
            return ConversionResult.success(0, 1);
        }
        JsonNode sensors = decoded.get("sensors");
        if (sensors == null || !sensors.isArray()) {
            log.debug("sensor config without sensors array: deviceId={} serviceType={}",
                    message.getDeviceId(), message.getServiceType());
            return ConversionResult.success(0, 1);
        }

        String stationcode = message.getDeviceId();
        String nodecode = JsonNodeUtils.text(decoded.get("nodeId"));
        if (nodecode.isEmpty()) {
            nodecode = JsonNodeUtils.text(message.getNodeId());
        }

        int processed = 0;
        int skipped = 0;
        for (JsonNode sensor : sensors) {
            if (sensor == null || !sensor.isObject()) {
                skipped++;
                continue;
            }
            int channel = sensor.path("channel").asInt(-1);
            if (channel < 0) {
                skipped++;
                continue;
            }
            String sensorType = JsonNodeUtils.text(sensor.get("sensorType"));
            String sensorCode = JsonNodeUtils.text(sensor.get("sensorCode"));
            if (sensorCode.isEmpty()) {
                sensorCode = "CH" + channel;
            }
            registry.register(stationcode, nodecode, channel, sensorCode, sensorType);
            processed++;
        }
        log.debug("sensor config applied: deviceId={} nodecode={} registered={} skipped={}",
                stationcode, nodecode, processed, skipped);
        return ConversionResult.success(processed, skipped);
    }
}
