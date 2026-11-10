package com.iot.platform.mq;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 上行契约测试：验证 ProxyTcp 用 Gson 序列化的 ServiceRequest JSON，能被 platform_java 的
 * UplinkMessage（Jackson）正确反序列化；以及 dtm 追加结构化字段后仍可消费。
 * 模拟生产环境 Spring Boot 的 ObjectMapper 配置（关闭 FAIL_ON_UNKNOWN_PROPERTIES）。
 */
public class UplinkMessageContractTest {

    private static final String SERVICEREQUEST_JSON = "{"
            + "\"deviceId\":\"30410007102\","
            + "\"nodeId\":null,"
            + "\"eventId\":123,"
            + "\"eventCreationTime\":null,"
            + "\"serviceType\":2,"
            + "\"serviceVersion\":0,"
            + "\"serviceDataEncoding\":0,"
            + "\"serviceDataProtocolVersion\":1,"
            + "\"serviceEncryption\":1,"
            + "\"serviceData\":\"QUJD\""
            + "}";

    private static final String DTM_ENRICHED_JSON = "{"
            + "\"deviceId\":\"30410007102\","
            + "\"nodeId\":\"\","
            + "\"eventId\":2774,"
            + "\"serviceType\":101,"
            + "\"serviceVersion\":1,"
            + "\"serviceDataEncoding\":2,"
            + "\"serviceDataProtocolVersion\":1,"
            + "\"serviceEncryption\":0,"
            + "\"serviceData\":\"AQI=\","
            + "\"serviceDataDecoded\":{\"appType\":1,\"action\":2},"
            + "\"protocolName\":\"GATEWAY_REBOOT\","
            + "\"transactionType\":\"downlink\","
            + "\"decodeStatus\":\"OK\","
            + "\"futureField\":\"ignored\""
            + "}";

    private ObjectMapper mapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    @Test
    public void gsonServiceRequestJsonParsesIntoUplinkMessage() throws Exception {
        UplinkMessage up = mapper().readValue(SERVICEREQUEST_JSON, UplinkMessage.class);

        assertEquals("deviceId 应正确映射", "30410007102", up.getDeviceId());
        assertEquals("serviceType 应正确映射", 2, up.getServiceType());
        assertEquals("serviceData(base64) 应原样透传", "QUJD", up.getServiceData());
        assertEquals("serviceEncryption 应正确映射（对齐 ServiceRequest）", 1, up.getServiceEncryption());
        assertNull("旧版透传报文没有结构化字段", up.getServiceDataDecoded());
    }

    @Test
    public void dtmStructuredFieldsAreConsumed() throws Exception {
        UplinkMessage up = mapper().readValue(DTM_ENRICHED_JSON, UplinkMessage.class);

        assertEquals(101, up.getServiceType());
        assertEquals("AQI=", up.getServiceData());
        assertEquals("GATEWAY_REBOOT", up.getProtocolName());
        assertEquals("downlink", up.getTransactionType());
        assertEquals("OK", up.getDecodeStatus());
        assertEquals(1, up.getServiceDataDecoded().path("appType").asInt());
        assertEquals(2, up.getServiceDataDecoded().path("action").asInt());
    }

    @Test
    public void unknownEnvelopeFieldsAreIgnoredEvenWithStrictMapper() throws Exception {
        ObjectMapper strict = new ObjectMapper();
        strict.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        UplinkMessage up = strict.readValue(DTM_ENRICHED_JSON, UplinkMessage.class);
        assertEquals("OK", up.getDecodeStatus());
    }
}
