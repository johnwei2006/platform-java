package com.iot.platform.service.converter;

import com.iot.platform.model.SensorMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 传感器配置元数据注册表：(stationcode, nodecode, channel) -> (sensorCode, sensorType)。
 *
 * <p>0X10 / 0X11 配置报文是仓库中唯一提供 sensorCode / sensorType 的来源；
 * 用内存并发 Map 缓存，避免 0X18 每条读数都回查 MongoDB 造成 N+1。
 * 设置容量上限，超出时淘汰一项防止元数据无限增长。
 */
@Component
public class SensorMetadataRegistry {

    private static final Logger log = LoggerFactory.getLogger(SensorMetadataRegistry.class);
    private static final int DEFAULT_MAX_ENTRIES = 100000;

    private final ConcurrentMap<String, SensorMetadata> metadata =
            new ConcurrentHashMap<String, SensorMetadata>();
    private final int maxEntries;

    public SensorMetadataRegistry() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public SensorMetadataRegistry(int maxEntries) {
        this.maxEntries = maxEntries > 0 ? maxEntries : DEFAULT_MAX_ENTRIES;
    }

    public static String key(String stationcode, String nodecode, int channel) {
        return clean(stationcode) + "|" + clean(nodecode) + "|" + channel;
    }

    public void register(String stationcode, String nodecode, int channel,
                         String sensorCode, String sensorType) {
        if (channel < 0) {
            return;
        }
        String k = key(stationcode, nodecode, channel);
        if (!metadata.containsKey(k) && metadata.size() >= maxEntries) {
            evictOne();
        }
        metadata.put(k, new SensorMetadata(clean(stationcode), clean(nodecode), channel,
                trim(sensorCode), trim(sensorType), false));
    }

    public SensorMetadata lookup(String stationcode, String nodecode, int channel) {
        return metadata.get(key(stationcode, nodecode, channel));
    }

    /**
     * 未命中时构造兜底元数据：sensorcode 使用 channel，sensortype 为空串。
     * 不在此处打印 warn，由转换器汇总后一次性告警，避免每条读数刷日志。
     */
    public SensorMetadata resolve(String stationcode, String nodecode, int channel) {
        SensorMetadata found = lookup(stationcode, nodecode, channel);
        if (found != null) {
            return found;
        }
        return new SensorMetadata(clean(stationcode), clean(nodecode), channel,
                "CH" + channel, "", true);
    }

    public int size() {
        return metadata.size();
    }

    public void clear() {
        metadata.clear();
    }

    private void evictOne() {
        Iterator<String> it = metadata.keySet().iterator();
        if (it.hasNext()) {
            metadata.remove(it.next());
            log.warn("sensor metadata registry reached capacity {}, evicted one entry", maxEntries);
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replace("|", "").replaceAll("[\\x00-\\x1f]", "");
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim().replaceAll("\u0000+$", "");
    }
}
