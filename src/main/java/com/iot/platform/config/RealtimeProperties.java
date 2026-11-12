package com.iot.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 设备实时状态缓存配置（Redis String JSON + TTL）。
 *
 * <p>TTL 过期即视为离线，不做定时离线扫描；所有参数都可用环境变量覆盖。
 */
@ConfigurationProperties(prefix = "realtime")
public class RealtimeProperties {

    private boolean enabled = true;
    /** 状态键 TTL，单位秒 */
    private long ttlSeconds = 120;
    private String keyPrefix = "device:status:";
    /** 单个状态 JSON 的字节上限，超出时按业务数据 -> 传感器摘要 -> 最小骨架逐级裁剪 */
    private int maxJsonBytes = 8192;
    /** 单次扫描每批 key 数量 */
    private int scanCount = 200;
    /** 读取状态的最大条数，避免大 key 空间一次性返回过多数据 */
    private int maxListSize = 1000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public int getMaxJsonBytes() {
        return maxJsonBytes;
    }

    public void setMaxJsonBytes(int maxJsonBytes) {
        this.maxJsonBytes = maxJsonBytes;
    }

    public int getScanCount() {
        return scanCount;
    }

    public void setScanCount(int scanCount) {
        this.scanCount = scanCount;
    }

    public int getMaxListSize() {
        return maxListSize;
    }

    public void setMaxListSize(int maxListSize) {
        this.maxListSize = maxListSize;
    }
}
