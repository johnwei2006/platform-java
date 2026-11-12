package com.iot.platform.model;

/**
 * 传感器配置元数据：由 0X10 / 0X11 配置报文提供，用于补全 0X18 实时读数的标签。
 *
 * <p>0X18 只按 channel 上报读数，sensorCode / sensorType 必须从配置元数据关联得到。
 * 未命中时由 {@code SensorMetadataRegistry} 构造兜底对象（{@code fallback=true}）。
 */
public class SensorMetadata {

    private String stationcode;
    private String nodecode;
    private int channel;
    private String sensorCode;
    private String sensorType;
    private boolean fallback;

    public SensorMetadata() {
    }

    public SensorMetadata(String stationcode, String nodecode, int channel,
                          String sensorCode, String sensorType, boolean fallback) {
        this.stationcode = stationcode;
        this.nodecode = nodecode;
        this.channel = channel;
        this.sensorCode = sensorCode;
        this.sensorType = sensorType;
        this.fallback = fallback;
    }

    public String getStationcode() {
        return stationcode;
    }

    public void setStationcode(String stationcode) {
        this.stationcode = stationcode;
    }

    public String getNodecode() {
        return nodecode;
    }

    public void setNodecode(String nodecode) {
        this.nodecode = nodecode;
    }

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

    public boolean isFallback() {
        return fallback;
    }

    public void setFallback(boolean fallback) {
        this.fallback = fallback;
    }
}
