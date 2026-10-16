package com.iot.platform.mq;

/**
 * 下行消息 DTO：平台发布到 platform.downlink，经 dtm 透传至网关（ProxyTcp）。
 * 字段对齐 ProxyTcp 的 ServiceRequest，便于网关直接消费。
 */
public class DownlinkMessage {

    private String deviceId;
    private String nodeId;
    private int serviceType;
    private int serviceVersion;
    /** 业务数据：原始字节的 base64 字符串 */
    private String serviceData;
    private int serviceDataEncoding;
    private int serviceDataProtocolVersion;
    private int serviceEncryption;
    private long eventId;

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

    public String getServiceData() {
        return serviceData;
    }

    public void setServiceData(String serviceData) {
        this.serviceData = serviceData;
    }

    public int getServiceDataEncoding() {
        return serviceDataEncoding;
    }

    public void setServiceDataEncoding(int serviceDataEncoding) {
        this.serviceDataEncoding = serviceDataEncoding;
    }

    public int getServiceDataProtocolVersion() {
        return serviceDataProtocolVersion;
    }

    public void setServiceDataProtocolVersion(int serviceDataProtocolVersion) {
        this.serviceDataProtocolVersion = serviceDataProtocolVersion;
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
}
