package com.iot.platform.mq;

/**
 * 上行消息 DTO：平台从 platform.uplink 消费到的设备上行数据（JSON）。
 * 与 dtm 透传的原始报文解耦，约定为 JSON 信封。
 */
public class UplinkMessage {

    private String deviceId;
    private String nodeId;
    private int serviceType;
    private int serviceVersion;
    private int serviceDataProtocolVersion;
    private int serviceDataEncoding;
    private int serviceEncryption;
    private long eventId;
    /** 业务数据：原始字节的 base64 字符串 */
    private String serviceData;
    private long timestamp;

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

    public int getServiceType() {
        return serviceType;
    }

    public void setServiceType(int serviceType) {
        this.serviceType = serviceType;
    }

    public int getServiceVersion() {
        return serviceVersion;
    }

    public void setServiceVersion(int serviceVersion) {
        this.serviceVersion = serviceVersion;
    }

    public int getServiceDataProtocolVersion() {
        return serviceDataProtocolVersion;
    }

    public void setServiceDataProtocolVersion(int serviceDataProtocolVersion) {
        this.serviceDataProtocolVersion = serviceDataProtocolVersion;
    }

    public int getServiceDataEncoding() {
        return serviceDataEncoding;
    }

    public void setServiceDataEncoding(int serviceDataEncoding) {
        this.serviceDataEncoding = serviceDataEncoding;
    }

    public int getServiceEncryption() {
        return serviceEncryption;
    }

    public void setServiceEncryption(int serviceEncryption) {
        this.serviceEncryption = serviceEncryption;
    }

    public long getEventId() {
        return eventId;
    }

    public void setEventId(long eventId) {
        this.eventId = eventId;
    }

    public String getServiceData() {
        return serviceData;
    }

    public void setServiceData(String serviceData) {
        this.serviceData = serviceData;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
