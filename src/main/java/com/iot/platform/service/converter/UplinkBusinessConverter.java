package com.iot.platform.service.converter;

import com.iot.platform.mq.UplinkMessage;

/**
 * 上行业务转换器扩展点：按 serviceType 分派，把 dtm 已解码的
 * {@code serviceDataDecoded} 转换为领域业务数据。
 *
 * <p>新增业务类型只需实现本接口并声明为 Spring Bean，分派器与接入服务无需改动。
 * 转换器不持有 MQ 副作用；异常由分派器隔离，不应影响上行消息确认。
 */
public interface UplinkBusinessConverter {

    boolean supports(int serviceType);

    ConversionResult convert(UplinkMessage message);
}
