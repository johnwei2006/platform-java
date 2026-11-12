package com.iot.platform.model;

/**
 * 一条传感器时序读数，对应 sensordata 超级表的一行。
 *
 * <p>普通列：ts / sensorvalue；标签：sensortype / stationcode / nodecode / sensorcode / unit。
 * 子表名由 sensorcode 派生：sensordata_{sensorcode}。
 */
public class SensorReading {

    private long ts;
    private String sensortype;
    private String stationcode;
    private String nodecode;
    private String sensorcode;
    private String unit;
    private double sensorvalue;

    public SensorReading() {
    }

    public SensorReading(long ts, String sensortype, String stationcode, String nodecode,
                         String sensorcode, String unit, double sensorvalue) {
        this.ts = ts;
        this.sensortype = sensortype;
        this.stationcode = stationcode;
        this.nodecode = nodecode;
        this.sensorcode = sensorcode;
        this.unit = unit;
        this.sensorvalue = sensorvalue;
    }

    public long getTs() {
        return ts;
    }

    public void setTs(long ts) {
        this.ts = ts;
    }

    public String getSensortype() {
        return sensortype;
    }

    public void setSensortype(String sensortype) {
        this.sensortype = sensortype;
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

    public String getSensorcode() {
        return sensorcode;
    }

    public void setSensorcode(String sensorcode) {
        this.sensorcode = sensorcode;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public double getSensorvalue() {
        return sensorvalue;
    }

    public void setSensorvalue(double sensorvalue) {
        this.sensorvalue = sensorvalue;
    }
}
