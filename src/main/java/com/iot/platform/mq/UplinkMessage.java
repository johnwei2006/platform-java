package com.iot.platform.mq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 上行消息 DTO：平台从 platform.uplink 消费到的设备上行数据（JSON）。
 * 原有字段与 ProxyTcp 的 ServiceRequest 对齐；dtm 追加的 serviceDataDecoded 等字段为可选。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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

    /** dtm 按 serviceType 解码出的结构化业务字段（可为 null：未知协议/解码失败） */
    private JsonNode serviceDataDecoded;
    private String protocolName;
    private String transactionType;
    private String decodeStatus;
    private String decodeError;

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

    public JsonNode getServiceDataDecoded() {
        return serviceDataDecoded;
    }

    public void setServiceDataDecoded(JsonNode serviceDataDecoded) {
        this.serviceDataDecoded = serviceDataDecoded;
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

    public String getDecodeError() {
        return decodeError;
    }

    public void setDecodeError(String decodeError) {
        this.decodeError = decodeError;
    }
}
