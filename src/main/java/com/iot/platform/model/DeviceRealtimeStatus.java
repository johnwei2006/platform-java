package com.iot.platform.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * 写入 Redis 的设备实时状态快照：在线状态 + 最近一次上行的最新业务数据。
 *
 * <p>这是旁路缓存，不是持久化实体；TTL 到期后 key 自动消失，查询接口据此判定离线。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceRealtimeStatus {

    private String deviceId;
    private String nodeId;
    /** ONLINE；TTL 过期后无此对象，查询方视为 OFFLINE */
    private String status;
    /** 平台最近收到上行的时间（epoch millis） */
    private long lastSeen;
    private long updatedAt;
    private int ttlSeconds;

    private int serviceType;
    private String protocolName;
    private String transactionType;
    private String decodeStatus;
    private long eventId;
    /** 设备上行信封自带的时间戳，原样保留 */
    private long deviceTimestamp;

    /** 最近一次解码后的完整业务数据（体积过大时会被裁剪） */
    private Map<String, Object> latestBusinessData;
    /** 最近一次有效的传感器读数摘要 */
    private List<SensorSnapshot> sensors;
    /** 是否因超过 max-json-bytes 被裁剪 */
    private boolean truncated;

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(int ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public int getServiceType() {
        return serviceType;
    }

    public void setServiceType(int serviceType) {
        this.serviceType = serviceType;
    }

    public String getProtocolName() {
        return protocolName;
    }

    public void setProtocolName(String protocolName) {
        this.protocolName = protocolName;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getDecodeStatus() {
        return decodeStatus;
    }

    public void setDecodeStatus(String decodeStatus) {
        this.decodeStatus = decodeStatus;
    }

    public long getEventId() {
        return eventId;
    }

    public void setEventId(long eventId) {
        this.eventId = eventId;
    }

    public long getDeviceTimestamp() {
        return deviceTimestamp;
    }

    public void setDeviceTimestamp(long deviceTimestamp) {
        this.deviceTimestamp = deviceTimestamp;
    }

    public Map<String, Object> getLatestBusinessData() {
        return latestBusinessData;
    }

    public void setLatestBusinessData(Map<String, Object> latestBusinessData) {
        this.latestBusinessData = latestBusinessData;
    }

    public List<SensorSnapshot> getSensors() {
        return sensors;
    }

    public void setSensors(List<SensorSnapshot> sensors) {
        this.sensors = sensors;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    /** 单通道传感器读数摘要；字段缺失时保留空串，不猜测业务含义。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SensorSnapshot {

        private int channel;
        private String sensorCode;
        private String sensorType;
        private String unit;
        private double sensorValue;

        public int getChannel() {
            return channel;
        }

        public void setChannel(int channel) {
            this.channel = channel;
        }

        public String getSensorCode() {
            return sensorCode;
        }

        public void setSensorCode(String sensorCode) {
            this.sensorCode = sensorCode;
        }

        public String getSensorType() {
            return sensorType;
        }

        public void setSensorType(String sensorType) {
            this.sensorType = sensorType;
        }

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }

        public double getSensorValue() {
            return sensorValue;
        }

        public void setSensorValue(double sensorValue) {
            this.sensorValue = sensorValue;
        }
    }
}
