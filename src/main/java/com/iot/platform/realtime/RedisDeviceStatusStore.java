package com.iot.platform.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.config.RealtimeProperties;
import com.iot.platform.model.DeviceRealtimeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 设备实时状态的 Redis 存储：每个设备一个 String key，值为 JSON，带 TTL。
 *
 * <p>读列表用 SCAN 而不是 KEYS，避免阻塞 Redis；所有 Redis 异常在此层限频告警并降级，
 * 不向调用方抛出，保证缓存故障不影响上行接入与 REST 接口。
 */
@Component
public class RedisDeviceStatusStore {

    private static final Logger log = LoggerFactory.getLogger(RedisDeviceStatusStore.class);
    private static final long WARN_INTERVAL_MS = 60000L;
    private static final int DEFAULT_MAX_JSON_BYTES = 8192;
    private static final int DEFAULT_SCAN_COUNT = 200;
    private static final int DEFAULT_MAX_LIST_SIZE = 1000;

    private final StringRedisTemplate template;
    private final ObjectMapper objectMapper;
    private final RealtimeProperties properties;
    private final AtomicLong lastWarnAt = new AtomicLong(0L);

    @Autowired
    public RedisDeviceStatusStore(ObjectProvider<StringRedisTemplate> provider,
                                  ObjectMapper objectMapper,
                                  RealtimeProperties properties) {
        this(provider.getIfAvailable(), objectMapper, properties);
    }

    public RedisDeviceStatusStore(StringRedisTemplate template,
                                  ObjectMapper objectMapper,
                                  RealtimeProperties properties) {
        this.template = template;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** 写入状态并刷新 TTL；返回 false 表示缓存不可用或写入被降级。 */
    public boolean write(DeviceRealtimeStatus status) {
        if (!available() || status == null) {
            return false;
        }
        String deviceId = status.getDeviceId();
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return false;
        }
        long ttl = effectiveTtl();
        try {
            String json = toJson(status);
            if (json == null) {
                return false;
            }
            template.opsForValue().set(key(deviceId), json, ttl, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            warnThrottled("write", e);
            return false;
        }
    }

    /** 读取单个设备状态；key 不存在（TTL 已过期）即视为离线。 */
    public Optional<DeviceRealtimeStatus> find(String deviceId) {
        if (!available() || deviceId == null || deviceId.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            String json = template.opsForValue().get(key(deviceId));
            if (json == null || json.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(objectMapper.readValue(json, DeviceRealtimeStatus.class));
        } catch (Exception e) {
            warnThrottled("read", e);
            return Optional.empty();
        }
    }

    /** 枚举当前全部在线设备状态；单条坏数据跳过，不影响其余结果。 */
    public List<DeviceRealtimeStatus> list() {
        if (!available()) {
            return Collections.emptyList();
        }
        try {
            List<String> keys = scanKeys(maxListSize());
            if (keys.isEmpty()) {
                return Collections.emptyList();
            }
            List<String> values = template.opsForValue().multiGet(keys);
            if (values == null) {
                return Collections.emptyList();
            }
            List<DeviceRealtimeStatus> result = new ArrayList<DeviceRealtimeStatus>();
            for (String json : values) {
                if (json == null || json.isEmpty()) {
                    continue;
                }
                try {
                    result.add(objectMapper.readValue(json, DeviceRealtimeStatus.class));
                } catch (Exception e) {
                    log.warn("skip malformed device status json: {}", e.getMessage());
                }
            }
            return result;
        } catch (Exception e) {
            warnThrottled("scan", e);
            return Collections.emptyList();
        }
    }

    private List<String> scanKeys(final int limit) {
        final String pattern = prefix() + "*";
        final int count = properties.getScanCount() > 0
                ? properties.getScanCount() : DEFAULT_SCAN_COUNT;
        List<String> keys = template.execute(new RedisCallback<List<String>>() {
            @Override
            public List<String> doInRedis(org.springframework.data.redis.connection.RedisConnection connection) {
                List<String> found = new ArrayList<String>();
                try (Cursor<byte[]> cursor = connection.scan(
                        ScanOptions.scanOptions().match(pattern).count(count).build())) {
                    while (cursor.hasNext() && found.size() < limit) {
                        byte[] raw = cursor.next();
                        if (raw != null) {
                            found.add(new String(raw, StandardCharsets.UTF_8));
                        }
                    }
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("redis SCAN cursor close failed", e);
                }
                return found;
            }
        });
        return keys == null ? Collections.<String>emptyList() : keys;
    }

    /**
     * 按字节预算逐级裁剪：完整业务数据 -> 传感器摘要 -> 最小在线骨架。
     * 裁剪只影响缓存中的快照，不影响 MongoDB 里的原始上行记录。
     */
    private String toJson(DeviceRealtimeStatus status) throws Exception {
        int limit = properties.getMaxJsonBytes() > 0
                ? properties.getMaxJsonBytes() : DEFAULT_MAX_JSON_BYTES;
        String json = objectMapper.writeValueAsString(status);
        if (utf8Length(json) <= limit) {
            return json;
        }
        status.setTruncated(true);
        status.setLatestBusinessData(null);
        json = objectMapper.writeValueAsString(status);
        if (utf8Length(json) <= limit) {
            return json;
        }
        status.setSensors(null);
        json = objectMapper.writeValueAsString(status);
        if (utf8Length(json) <= limit) {
            return json;
        }
        DeviceRealtimeStatus minimal = new DeviceRealtimeStatus();
        minimal.setDeviceId(status.getDeviceId());
        minimal.setStatus(status.getStatus());
        minimal.setLastSeen(status.getLastSeen());
        minimal.setUpdatedAt(status.getUpdatedAt());
        minimal.setTtlSeconds(status.getTtlSeconds());
        minimal.setServiceType(status.getServiceType());
        minimal.setDecodeStatus(status.getDecodeStatus());
        minimal.setEventId(status.getEventId());
        minimal.setTruncated(true);
        json = objectMapper.writeValueAsString(minimal);
        if (utf8Length(json) <= limit) {
            return json;
        }
        log.warn("device status json still exceeds {} bytes even after trimming: deviceId={}",
                limit, status.getDeviceId());
        return null;
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private String key(String deviceId) {
        return prefix() + deviceId;
    }

    private String prefix() {
        String prefix = properties.getKeyPrefix();
        return prefix == null || prefix.isEmpty() ? "device:status:" : prefix;
    }

    private long effectiveTtl() {
        return properties.getTtlSeconds() > 0 ? properties.getTtlSeconds() : 1L;
    }

    private int maxListSize() {
        return properties.getMaxListSize() > 0
                ? properties.getMaxListSize() : DEFAULT_MAX_LIST_SIZE;
    }

    private boolean available() {
        return template != null && properties.isEnabled();
    }

    private void warnThrottled(String operation, Exception e) {
        long now = System.currentTimeMillis();
        long last = lastWarnAt.get();
        if (now - last < WARN_INTERVAL_MS) {
            return;
        }
        if (lastWarnAt.compareAndSet(last, now)) {
            log.warn("redis device status {} failed (degraded, uplink unaffected): {}",
                    operation, e.getMessage());
        }
    }
}
