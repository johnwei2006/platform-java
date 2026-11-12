package com.iot.platform.timeseries;

import com.iot.platform.model.SensorReading;

import java.util.List;

/**
 * 传感器读数写入抽象。转换器只依赖此接口，便于在无时序库环境用 Mockito 替换。
 *
 * <p>返回实际写入成功的条数；实现必须自行隔离异常，不得向调用方抛出。
 */
public interface SensorReadingWriter {

    int write(List<SensorReading> readings);
}
