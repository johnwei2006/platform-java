package com.iot.platform.service.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iot.platform.config.TdEngineProperties;
import com.iot.platform.model.SensorReading;
import com.iot.platform.mq.UplinkMessage;
import com.iot.platform.timeseries.SensorReadingWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SensorDataConverterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private SensorMetadataRegistry registry;
    private TdEngineProperties properties;
    private SensorReadingWriter writer;
    private SensorDataConverter converter;

    @Before
    public void setUp() {
        registry = new SensorMetadataRegistry();
        registry.register("GW1", "NODE1", 1, "TEMP01", "1");
        properties = new TdEngineProperties();
        properties.getUnitBySensorType().put("1", "C");
        writer = mock(SensorReadingWriter.class);
        when(writer.write(any())).thenAnswer(inv -> ((List<?>) inv.getArgument(0)).size());
        converter = new SensorDataConverter(registry, properties, writer);
    }

    @Test
    public void supportsOnlySensorDataReport() {
        assertTrue(converter.supports(0x18));
        assertFalse(converter.supports(0x10));
        assertFalse(converter.supports(0x11));
    }

    @Test
    public void convertsMultipleChannelsAndFiltersInvalidReadings() throws Exception {
        long expectedTs = LocalDateTime.of(2026, 11, 12, 10, 20, 30)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        UplinkMessage up = message(
                "{\"nodeId\":\"NODE1\",\"collectTime\":{\"year\":26,\"month\":11,\"date\":12,"
                        + "\"hour\":10,\"minute\":20,\"second\":30},"
                        + "\"collectResponse\":0,\"sensorSize\":3,\"sensors\":["
                        + "{\"channel\":1,\"channelResult\":0,\"sensorValue\":235},"
                        + "{\"channel\":2,\"channelResult\":1,\"sensorValue\":999},"
                        + "{\"channel\":3,\"channelResult\":0,\"sensorValue\":42}]}");

        ConversionResult result = converter.convert(up);

        assertEquals(ConversionResult.STATUS_OK, result.getStatus());
        assertEquals(2, result.getProcessed());
        assertEquals(1, result.getSkipped());

        List<SensorReading> readings = capturedReadings();
        assertEquals(2, readings.size());
        SensorReading first = readings.get(0);
        assertEquals(expectedTs, first.getTs());
        assertEquals("1", first.getSensortype());
        assertEquals("GW1", first.getStationcode());
        assertEquals("NODE1", first.getNodecode());
        assertEquals("TEMP01", first.getSensorcode());
        assertEquals("C", first.getUnit());
        assertEquals(235.0, first.getSensorvalue(), 0.0001);

        SensorReading fallback = readings.get(1);
        assertEquals("CH3", fallback.getSensorcode());
        assertEquals("", fallback.getSensortype());
        assertEquals("", fallback.getUnit());
        assertEquals(42.0, fallback.getSensorvalue(), 0.0001);
    }

    @Test
    public void fallsBackToMessageTimestampWhenCollectTimeMissing() throws Exception {
        UplinkMessage up = message(
                "{\"nodeId\":\"NODE1\",\"sensors\":[{\"channel\":1,"
                        + "\"channelResult\":0,\"sensorValue\":1}]}");
        up.setTimestamp(1700000000L); // 秒级

        converter.convert(up);

        assertEquals(1700000000000L, capturedReadings().get(0).getTs());
    }

    @Test
    public void fallsBackToCurrentTimeWhenNoTimestamp() throws Exception {
        UplinkMessage up = message(
                "{\"nodeId\":\"NODE1\",\"sensors\":[{\"channel\":1,"
                        + "\"channelResult\":0,\"sensorValue\":1}]}");
        long before = System.currentTimeMillis();

        converter.convert(up);

        long ts = capturedReadings().get(0).getTs();
        assertTrue(ts >= before);
        assertTrue(ts <= System.currentTimeMillis() + 1000);
    }

    @Test
    public void resolvesTime6ObjectHexAndEpochForms() throws Exception {
        long expected = LocalDateTime.of(2026, 11, 12, 10, 20, 30)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        assertEquals(expected, SensorDataConverter.resolveTimestamp(
                mapper.readTree("{\"year\":26,\"month\":11,\"date\":12,"
                        + "\"hour\":10,\"minute\":20,\"second\":30}"), 111L));
        assertEquals(expected, SensorDataConverter.resolveTimestamp(
                new TextNode("1a0b0c0a141e"), 111L));
        assertEquals(1700000000000L, SensorDataConverter.resolveTimestamp(
                new LongNode(1700000000L), 111L));
        assertEquals(111L, SensorDataConverter.resolveTimestamp(
                mapper.readTree("{\"year\":-1}"), 111L));
        assertEquals(111L, SensorDataConverter.resolveTimestamp(null, 111L));
        assertEquals(2000000000000L, SensorDataConverter.normalizeEpoch(2000000000000L));
        assertEquals(0L, SensorDataConverter.normalizeEpoch(0L));
    }

    @Test
    public void skipsMessageWithoutSensorsArray() throws Exception {
        ConversionResult result = converter.convert(message("{\"nodeId\":\"NODE1\"}"));
        assertEquals(0, result.getProcessed());
        assertEquals(1, result.getSkipped());
        verify(writer, never()).write(any());
    }

    @Test
    public void writerFailureDoesNotFailConversion() throws Exception {
        doThrow(new RuntimeException("tdengine down")).when(writer).write(any());
        UplinkMessage up = message(
                "{\"nodeId\":\"NODE1\",\"sensors\":[{\"channel\":1,"
                        + "\"channelResult\":0,\"sensorValue\":1}]}");

        ConversionResult result = converter.convert(up);

        assertEquals(ConversionResult.STATUS_OK, result.getStatus());
        assertEquals(1, result.getProcessed());
    }

    @SuppressWarnings("unchecked")
    private List<SensorReading> capturedReadings() {
        ArgumentCaptor<List<SensorReading>> captor =
                (ArgumentCaptor<List<SensorReading>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
        verify(writer).write(captor.capture());
        return captor.getValue();
    }

    private UplinkMessage message(String decodedJson) throws Exception {
        UplinkMessage up = new UplinkMessage();
        up.setDeviceId("GW1");
        up.setServiceType(0x18);
        up.setDecodeStatus("OK");
        up.setServiceDataDecoded(mapper.readTree(decodedJson));
        return up;
    }
}
