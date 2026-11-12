package com.iot.platform.service.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.iot.platform.mq.UplinkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 按 serviceType 把已解码的上行消息分派给业务转换器。
 *
 * <p>分派器是上行接入链路上的旁路：decodeStatus 非 OK 或没有结构化字段时直接跳过；
 * 单个转换器抛异常只记录日志并继续尝试其余转换器，绝不向调用方抛出，
 * 保证业务转换/时序库故障不会触发 RabbitMQ 重试或死信。
 */
@Service
public class UplinkBusinessDispatcher {

    private static final Logger log = LoggerFactory.getLogger(UplinkBusinessDispatcher.class);

    private final List<UplinkBusinessConverter> converters;

    @Autowired
    public UplinkBusinessDispatcher(ObjectProvider<UplinkBusinessConverter> provider) {
        this(provider.orderedStream().collect(Collectors.toList()));
    }

    public UplinkBusinessDispatcher(List<UplinkBusinessConverter> converters) {
        this.converters = converters == null
                ? Collections.<UplinkBusinessConverter>emptyList()
                : new ArrayList<UplinkBusinessConverter>(converters);
    }

    public ConversionResult dispatch(UplinkMessage message) {
        if (message == null) {
            return ConversionResult.skipped("null uplink message");
        }
        if (!canConvert(message)) {
            return ConversionResult.skipped("decodeStatus not OK or serviceDataDecoded absent");
        }

        long start = System.currentTimeMillis();
        ConversionResult aggregate = ConversionResult.notHandled();
        for (UplinkBusinessConverter converter : converters) {
            if (converter == null || !converter.supports(message.getServiceType())) {
                continue;
            }
            try {
                aggregate = aggregate.merge(converter.convert(message));
            } catch (Exception e) {
                log.error("business converter failed: deviceId={} serviceType={} converter={} reason={}",
                        message.getDeviceId(), message.getServiceType(),
                        converter.getClass().getSimpleName(), e.getMessage());
                aggregate = aggregate.merge(ConversionResult.failure(
                        converter.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        log.info("uplink business dispatch: deviceId={} serviceType={} result={} costMs={}",
                message.getDeviceId(), message.getServiceType(), aggregate,
                System.currentTimeMillis() - start);
        return aggregate;
    }

    private boolean canConvert(UplinkMessage message) {
        JsonNode decoded = message.getServiceDataDecoded();
        if (decoded == null || decoded.isNull() || !decoded.isObject()) {
            return false;
        }
        String status = message.getDecodeStatus();
        // 老消息可能不带 decodeStatus，此时只要存在结构化字段就允许转换
        return status == null || status.isEmpty() || "OK".equalsIgnoreCase(status);
    }
}
