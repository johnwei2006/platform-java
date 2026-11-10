package com.iot.platform.mq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 下行消息 DTO：平台发布到 platform.downlink，经 dtm 编码后送网关（ProxyTcp）。
 * 既支持旧的 base64 serviceData，也支持结构化 serviceDataDecoded（由 dtm 编码为二进制）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
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
    /** 结构化业务字段；存在时 dtm 会优先用它编码二进制 serviceData */
    private JsonNode serviceDataDecoded;

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

    public JsonNode getServiceDataDecoded() {
        return serviceDataDecoded;
    }

    public void setServiceDataDecoded(JsonNode serviceDataDecoded) {
        this.serviceDataDecoded = serviceDataDecoded;
    }
}
