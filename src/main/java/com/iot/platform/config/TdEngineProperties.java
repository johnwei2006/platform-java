package com.iot.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TDengine 时序库连接与建模配置。
 *
 * <p>连接参数只通过环境变量/配置文件注入，口令不会出现在日志里。
 * 默认 enabled=true 但采用懒连接：首次写入时才建库/建表，时序库不可用不影响应用启动。
 */
@ConfigurationProperties(prefix = "tdengine")
public class TdEngineProperties {

    private boolean enabled = true;
    private String host = "localhost";
    private int port = 6030;
    private String username = "root";
    private String password = "taosdata";
    private String database = "iot";
    private String stable = "sensordata";
    /** 标签列 NCHAR 长度 */
    private int tagLength = 64;
    /** 子表名最大长度（含 sensordata_ 前缀） */
    private int maxSubTableNameLength = 64;
    /** 传感器类型码 -> 单位；未配置的类型使用空串 */
    private Map<String, String> unitBySensorType = new LinkedHashMap<String, String>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getStable() {
        return stable;
    }

    public void setStable(String stable) {
        this.stable = stable;
    }

    public int getTagLength() {
        return tagLength;
    }

    public void setTagLength(int tagLength) {
        this.tagLength = tagLength;
    }

    public int getMaxSubTableNameLength() {
        return maxSubTableNameLength;
    }

    public void setMaxSubTableNameLength(int maxSubTableNameLength) {
        this.maxSubTableNameLength = maxSubTableNameLength;
    }

    public Map<String, String> getUnitBySensorType() {
        return unitBySensorType;
    }

    public void setUnitBySensorType(Map<String, String> unitBySensorType) {
        this.unitBySensorType = unitBySensorType == null
                ? new LinkedHashMap<String, String>() : unitBySensorType;
    }

    public String unitFor(String sensorType) {
        String unit = unitBySensorType.get(String.valueOf(sensorType));
        return unit == null ? "" : unit;
    }
}
