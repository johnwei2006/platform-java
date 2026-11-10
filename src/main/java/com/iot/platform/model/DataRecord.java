package com.iot.platform.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.Map;

@Document(collection = "data_records")
public class DataRecord {

    @Id
    private String id;
    private String deviceId;
    private int serviceType;
    private int serviceVersion;
    /** 业务数据原始字节的 base64 */
    private String serviceData;
    private long eventId;
    private Date createdAt;
    /** dtm 解码出的结构化业务字段 */
    private Map<String, Object> serviceDataDecoded;
    private String protocolName;
    private String transactionType;
    private String decodeStatus;
    private String decodeError;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
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

    public long getEventId() {
        return eventId;
    }

    public void setEventId(long eventId) {
        this.eventId = eventId;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Map<String, Object> getServiceDataDecoded() {
        return serviceDataDecoded;
    }

    public void setServiceDataDecoded(Map<String, Object> serviceDataDecoded) {
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
