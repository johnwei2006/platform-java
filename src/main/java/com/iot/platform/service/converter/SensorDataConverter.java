package com.iot.platform.service.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.iot.platform.config.TdEngineProperties;
import com.iot.platform.model.SensorMetadata;
import com.iot.platform.model.SensorReading;
import com.iot.platform.mq.UplinkMessage;
import com.iot.platform.timeseries.SensorReadingWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 传感器实时数据转换器：0X18 GATEWAY_SENSOR_DATA_REPORT(24)。
 *
 * <p>dtm 已把 serviceData 解码成结构化字段，这里只做业务映射：
 * collectTime -> ts；channel 经元数据关联得到 sensorcode / sensortype；
 * unit 由 sensorType 配置映射；channelResult 非 0 的无效读数跳过；
 * 最后把多条读数交给 SensorReadingWriter 归档到各自子表。
 */
@Component
public class SensorDataConverter implements UplinkBusinessConverter {

    public static final int SERVICE_SENSOR_DATA_REPORT = 0x18;

    private static final Logger log = LoggerFactory.getLogger(SensorDataConverter.class);
    private static final long EPOCH_MILLIS_THRESHOLD = 100000000000L;

    private final SensorMetadataRegistry registry;
    private final TdEngineProperties properties;
    private final SensorReadingWriter writer;

    @Autowired
    public SensorDataConverter(SensorMetadataRegistry registry,
                               TdEngineProperties properties,
                               SensorReadingWriter writer) {
        this.registry = registry;
        this.properties = properties;
        this.writer = writer;
    }

    @Override
    public boolean supports(int serviceType) {
        return serviceType == SERVICE_SENSOR_DATA_REPORT;
    }

    @Override
    public ConversionResult convert(UplinkMessage message) {
        JsonNode decoded = message.getServiceDataDecoded();
        if (decoded == null || !decoded.isObject()) {
            return ConversionResult.success(0, 1);
        }
        JsonNode sensors = decoded.get("sensors");
        if (sensors == null || !sensors.isArray()) {
            log.warn("sensor data report without sensors array: deviceId={} serviceType={}",
                    message.getDeviceId(), message.getServiceType());
            return ConversionResult.success(0, 1);
        }

        long ts = resolveTimestamp(decoded.get("collectTime"), fallbackMillis(message));
        String stationcode = JsonNodeUtils.text(message.getDeviceId());
        String nodecode = JsonNodeUtils.text(decoded.get("nodeId"));
        if (nodecode.isEmpty()) {
            nodecode = JsonNodeUtils.text(message.getNodeId());
        }

        List<SensorReading> readings = new ArrayList<SensorReading>();
        int skipped = 0;
        int fallbackCount = 0;
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
            if (sensor.path("channelResult").asInt(0) != 0) {
                skipped++;
                continue;
            }
            double sensorValue = sensor.path("sensorValue").asDouble(Double.NaN);
            if (Double.isNaN(sensorValue) || Double.isInfinite(sensorValue)) {
                skipped++;
                continue;
            }
            SensorMetadata meta = registry.resolve(stationcode, nodecode, channel);
            if (meta.isFallback()) {
                fallbackCount++;
            }
            readings.add(new SensorReading(ts, meta.getSensorType(), stationcode, nodecode,
                    meta.getSensorCode(), properties.unitFor(meta.getSensorType()), sensorValue));
        }

        int written = 0;
        if (!readings.isEmpty()) {
            try {
                written = writer == null ? 0 : writer.write(readings);
            } catch (Exception e) {
                log.warn("sensor reading writer threw exception: deviceId={} count={} reason={}",
                        stationcode, readings.size(), e.getMessage());
            }
        }
        if (fallbackCount > 0) {
            log.warn("sensor metadata missing, fallback to channel: deviceId={} nodecode={} count={}",
                    stationcode, nodecode, fallbackCount);
        }
        if (readings.size() != written) {
            log.warn("TDengine write incomplete: deviceId={} serviceType={} expected={} written={}",
                    stationcode, message.getServiceType(), readings.size(), written);
        }
        log.debug("sensor data converted: deviceId={} nodecode={} readings={} skipped={} ts={}",
                stationcode, nodecode, readings.size(), skipped, ts);
        return ConversionResult.success(readings.size(), skipped);
    }

    private long fallbackMillis(UplinkMessage message) {
        long ts = normalizeEpoch(message.getTimestamp());
        return ts > 0 ? ts : System.currentTimeMillis();
    }

    /**
     * 统一解析设备采集时间：time6 对象、hex 字符串、秒/毫秒时间戳三种形态；
     * 任一形态非法都回退到 fallbackMillis，不抛异常。
     */
    static long resolveTimestamp(JsonNode collectTime, long fallbackMillis) {
        if (collectTime == null || collectTime.isNull()) {
            return fallbackMillis;
        }
        if (collectTime.isNumber()) {
            long value = normalizeEpoch(collectTime.asLong());
            return value > 0 ? value : fallbackMillis;
        }
        if (collectTime.isTextual()) {
            String text = collectTime.asText().trim();
            byte[] timeBytes = parseHex(text);
            if (timeBytes != null && timeBytes.length == 6) {
                return fromSixBytes(timeBytes, fallbackMillis);
            }
            try {
                long value = normalizeEpoch(Long.parseLong(text));
                return value > 0 ? value : fallbackMillis;
            } catch (NumberFormatException e) {
                return fallbackMillis;
            }
        }
        if (collectTime.isObject()) {
            long value = fromTimeObject(collectTime, fallbackMillis);
            if (value != fallbackMillis) {
                return value;
            }
            JsonNode hex = collectTime.get("hex");
            if (hex != null && hex.isTextual()) {
                byte[] timeBytes = parseHex(hex.asText().trim());
                if (timeBytes != null && timeBytes.length == 6) {
                    return fromSixBytes(timeBytes, fallbackMillis);
                }
            }
        }
        return fallbackMillis;
    }

    private static long fromTimeObject(JsonNode node, long fallbackMillis) {
        int year = node.path("year").asInt(-1);
        int month = node.path("month").asInt(-1);
        int date = node.path("date").asInt(-1);
        int hour = node.path("hour").asInt(-1);
        int minute = node.path("minute").asInt(-1);
        int second = node.path("second").asInt(-1);
        if (year < 0 || month < 1 || month > 12 || date < 1 || date > 31
                || hour < 0 || hour > 23 || minute < 0 || minute > 59
                || second < 0 || second > 60) {
            return fallbackMillis;
        }
        int fullYear = year < 100 ? 2000 + year : year;
        try {
            LocalDateTime local = LocalDateTime.of(fullYear, month, date, hour, minute,
                    Math.min(second, 59));
            return local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeException e) {
            return fallbackMillis;
        }
    }

    private static long fromSixBytes(byte[] b, long fallbackMillis) {
        return fromTimeParts(b[0] & 0xFF, b[1] & 0xFF, b[2] & 0xFF,
                b[3] & 0xFF, b[4] & 0xFF, b[5] & 0xFF, fallbackMillis);
    }

    private static long fromTimeParts(int year, int month, int date, int hour, int minute,
                                      int second, long fallbackMillis) {
        if (month < 1 || month > 12 || date < 1 || date > 31 || hour > 23
                || minute > 59 || second > 60) {
            return fallbackMillis;
        }
        int fullYear = year < 100 ? 2000 + year : year;
        try {
            LocalDateTime local = LocalDateTime.of(fullYear, month, date, hour, minute,
                    Math.min(second, 59));
            return local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeException e) {
            return fallbackMillis;
        }
    }

    static long normalizeEpoch(long value) {
        if (value <= 0) {
            return 0;
        }
        return value < EPOCH_MILLIS_THRESHOLD ? value * 1000 : value;
    }

    private static byte[] parseHex(String text) {
        if (text == null || text.isEmpty() || (text.length() % 2) != 0
                || !text.matches("[0-9a-fA-F]+")) {
            return null;
        }
        byte[] out = new byte[text.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(text.charAt(i * 2), 16);
            int lo = Character.digit(text.charAt(i * 2 + 1), 16);
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
