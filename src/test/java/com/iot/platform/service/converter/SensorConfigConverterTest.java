package com.iot.platform.service.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.model.SensorMetadata;
import com.iot.platform.mq.UplinkMessage;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SensorConfigConverterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private SensorMetadataRegistry registry;
    private SensorConfigConverter converter;

    @Before
    public void setUp() {
        registry = new SensorMetadataRegistry();
        converter = new SensorConfigConverter(registry);
    }

    @Test
    public void supportsConfigServiceTypesOnly() {
        assertTrue(converter.supports(0x10));
        assertTrue(converter.supports(0x11));
        assertFalse(converter.supports(0x18));
    }

    @Test
    public void registersSensorMetadataFromConfigReport() throws Exception {
        UplinkMessage up = message(0x10,
                "{\"nodeId\":\"NODE1\",\"sensorSize\":2,\"sensors\":["
                        + "{\"channel\":1,\"sensorType\":1,\"sensorCode\":\"TEMP01\"},"
                        + "{\"channel\":2,\"sensorType\":2,\"sensorCode\":\"HUM01\"}]}");

        ConversionResult result = converter.convert(up);

        assertEquals(2, result.getProcessed());
        assertEquals(0, result.getSkipped());
        SensorMetadata first = registry.lookup("GW1", "NODE1", 1);
        assertNotNull(first);
        assertEquals("TEMP01", first.getSensorCode());
        assertEquals("1", first.getSensorType());
        assertEquals("HUM01", registry.lookup("GW1", "NODE1", 2).getSensorCode());
        assertEquals(2, registry.size());
    }

    @Test
    public void overwritesMetadataForSameKey() throws Exception {
        converter.convert(message(0x10,
                "{\"nodeId\":\"NODE1\",\"sensors\":[{\"channel\":1,"
                        + "\"sensorType\":1,\"sensorCode\":\"OLD\"}]}"));
        converter.convert(message(0x11,
                "{\"nodeId\":\"NODE1\",\"sensors\":[{\"channel\":1,"
                        + "\"sensorType\":3,\"sensorCode\":\"NEW\"}]}"));

        assertEquals(1, registry.size());
        SensorMetadata meta = registry.lookup("GW1", "NODE1", 1);
        assertEquals("NEW", meta.getSensorCode());
        assertEquals("3", meta.getSensorType());
        assertFalse(meta.isFallback());
    }

    @Test
    public void fallsBackToEnvelopeNodeIdWhenDecodedAbsent() throws Exception {
        UplinkMessage up = message(0x10,
                "{\"sensors\":[{\"channel\":1,\"sensorType\":1,\"sensorCode\":\"TEMP01\"}]}");
        up.setNodeId("ENV_NODE");

        converter.convert(up);

        assertNotNull(registry.lookup("GW1", "ENV_NODE", 1));
        assertEquals("TEMP01", registry.lookup("GW1", "ENV_NODE", 1).getSensorCode());
    }

    @Test
    public void skipsMalformedConfigEntries() throws Exception {
        ConversionResult missing = converter.convert(message(0x10, "{\"result\":0}"));
        assertEquals(0, missing.getProcessed());
        assertEquals(1, missing.getSkipped());

        ConversionResult partial = converter.convert(message(0x10,
                "{\"nodeId\":\"NODE1\",\"sensors\":["
                        + "{\"sensorType\":1,\"sensorCode\":\"NO_CHANNEL\"},"
                        + "{\"channel\":7,\"sensorType\":1}]}"));
        assertEquals(1, partial.getProcessed());
        assertEquals(1, partial.getSkipped());
        // sensorCode 缺省时用通道兜底，保证子表名合法
        assertEquals("CH7", registry.lookup("GW1", "NODE1", 7).getSensorCode());
    }

    private UplinkMessage message(int serviceType, String decodedJson) throws Exception {
        UplinkMessage up = new UplinkMessage();
        up.setDeviceId("GW1");
        up.setServiceType(serviceType);
        up.setDecodeStatus("OK");
        up.setServiceDataDecoded(mapper.readTree(decodedJson));
        return up;
    }
}
