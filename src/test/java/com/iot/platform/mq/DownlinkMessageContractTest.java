package com.iot.platform.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 下行契约：结构化字段与旧 base64 通道都对齐 dtm 的 EnvelopeService 输入。 */
public class DownlinkMessageContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void structuredPayloadIsSerializedForDtmEncoding() throws Exception {
        DownlinkMessage dm = new DownlinkMessage();
        dm.setDeviceId("30410007102");
        dm.setServiceType(101);
        dm.setServiceDataEncoding(2);
        dm.setServiceDataProtocolVersion(1);
        ObjectNode decoded = mapper.createObjectNode();
        decoded.put("appType", 1);
        decoded.put("action", 9);
        dm.setServiceDataDecoded(decoded);

        JsonNode json = mapper.readTree(mapper.writeValueAsBytes(dm));
        assertEquals("30410007102", json.path("deviceId").asText());
        assertEquals(101, json.path("serviceType").asInt());
        assertEquals(9, json.path("serviceDataDecoded").path("action").asInt());
        assertFalse("null serviceData 不应序列化", json.has("serviceData"));
    }

    @Test
    public void legacyBase64PayloadStillSupported() throws Exception {
        DownlinkMessage dm = new DownlinkMessage();
        dm.setDeviceId("g1");
        dm.setServiceType(101);
        dm.setServiceData("AQI=");

        JsonNode json = mapper.readTree(mapper.writeValueAsBytes(dm));
        assertEquals("AQI=", json.path("serviceData").asText());
        assertFalse(json.has("serviceDataDecoded"));
        assertTrue(json.has("serviceDataEncoding"));
    }
}
