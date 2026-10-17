package com.iot.platform.mq;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 上行契约测试：验证 ProxyTcp 用 Gson 序列化的 ServiceRequest JSON，能被 platform_java 的
 * UplinkMessage（Jackson）正确反序列化。模拟生产环境 Spring Boot 的 ObjectMapper 配置
 * （关闭 FAIL_ON_UNKNOWN_PROPERTIES），与 DataIngestionService 实际行为一致。
 *
 * <p>该测试是「ProxyTcp → dtm(透传) → platform_java」上行端到端打通的关键证据：
 * 只要 JSON key 与字段对齐（尤其是 serviceEncryption），网关上行即可被后台正确消费。
 */
public class UplinkMessageContractTest {

    // 与 ProxyTcp ServiceRequest（Gson）输出一致的 JSON：
    // serviceData 为原始字节的 base64（"QUJD" = "ABC"）；serviceEncryption 为对齐后的 key。
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

    @Test
    public void gsonServiceRequestJsonParsesIntoUplinkMessage() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        UplinkMessage up = mapper.readValue(SERVICEREQUEST_JSON, UplinkMessage.class);

        assertEquals("deviceId 应正确映射", "30410007102", up.getDeviceId());
        assertEquals("serviceType 应正确映射", 2, up.getServiceType());
        assertEquals("serviceData(base64) 应原样透传", "QUJD", up.getServiceData());
        // 关键：serviceEncryption key 现在能被 UplinkMessage 识别（改名前会被 Jackson 忽略丢弃）
        assertEquals("serviceEncryption 应正确映射（对齐 ServiceRequest）", 1, up.getServiceEncryption());
    }
}
