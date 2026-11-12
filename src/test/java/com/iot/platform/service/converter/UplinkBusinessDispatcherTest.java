package com.iot.platform.service.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.mq.UplinkMessage;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UplinkBusinessDispatcherTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void dispatchesOnlyToMatchingConverter() throws Exception {
        UplinkBusinessConverter sensor = mock(UplinkBusinessConverter.class);
        UplinkBusinessConverter other = mock(UplinkBusinessConverter.class);
        when(sensor.supports(24)).thenReturn(true);
        when(sensor.convert(any(UplinkMessage.class)))
                .thenReturn(ConversionResult.success(2, 1));
        when(other.supports(24)).thenReturn(false);

        UplinkBusinessDispatcher dispatcher =
                new UplinkBusinessDispatcher(Arrays.asList(sensor, other));
        ConversionResult result = dispatcher.dispatch(message(24, "{\"sensors\":[]}", "OK"));

        assertTrue(result.isHandled());
        assertEquals(ConversionResult.STATUS_OK, result.getStatus());
        assertEquals(2, result.getProcessed());
        assertEquals(1, result.getSkipped());
        verify(other, never()).convert(any(UplinkMessage.class));
    }

    @Test
    public void isolatesConverterFailureAndContinues() throws Exception {
        UplinkBusinessConverter broken = mock(UplinkBusinessConverter.class);
        UplinkBusinessConverter healthy = mock(UplinkBusinessConverter.class);
        when(broken.supports(24)).thenReturn(true);
        when(broken.convert(any(UplinkMessage.class)))
                .thenThrow(new RuntimeException("boom"));
        when(healthy.supports(24)).thenReturn(true);
        when(healthy.convert(any(UplinkMessage.class)))
                .thenReturn(ConversionResult.success(1, 0));

        UplinkBusinessDispatcher dispatcher =
                new UplinkBusinessDispatcher(Arrays.asList(broken, healthy));
        ConversionResult result = dispatcher.dispatch(message(24, "{\"sensors\":[]}", "OK"));

        assertEquals(ConversionResult.STATUS_ERROR, result.getStatus());
        assertEquals(1, result.getProcessed());
        verify(healthy).convert(any(UplinkMessage.class));
    }

    @Test
    public void skipsUndecodableMessages() throws Exception {
        UplinkBusinessConverter converter = mock(UplinkBusinessConverter.class);
        UplinkBusinessDispatcher dispatcher =
                new UplinkBusinessDispatcher(Collections.singletonList(converter));

        ConversionResult failed = dispatcher.dispatch(message(24, "{\"sensors\":[]}", "FAILED"));
        assertEquals(ConversionResult.STATUS_SKIPPED, failed.getStatus());
        assertFalse(failed.isHandled());

        UplinkMessage noDecoded = message(24, null, "OK");
        ConversionResult absent = dispatcher.dispatch(noDecoded);
        assertEquals(ConversionResult.STATUS_SKIPPED, absent.getStatus());
        verify(converter, never()).supports(anyInt());
    }

    @Test
    public void returnsNoConverterWhenNothingRegistered() throws Exception {
        UplinkBusinessDispatcher dispatcher = new UplinkBusinessDispatcher(
                Collections.<UplinkBusinessConverter>emptyList());
        ConversionResult result = dispatcher.dispatch(message(24, "{\"sensors\":[]}", "OK"));
        assertEquals(ConversionResult.STATUS_NO_CONVERTER, result.getStatus());
        assertFalse(result.isHandled());
    }

    @Test
    public void allowsLegacyMessagesWithoutDecodeStatus() throws Exception {
        UplinkBusinessConverter converter = mock(UplinkBusinessConverter.class);
        when(converter.supports(24)).thenReturn(true);
        when(converter.convert(any(UplinkMessage.class)))
                .thenReturn(ConversionResult.success(1, 0));

        UplinkBusinessDispatcher dispatcher =
                new UplinkBusinessDispatcher(Collections.singletonList(converter));
        ConversionResult result = dispatcher.dispatch(message(24, "{\"a\":1}", null));
        assertEquals(ConversionResult.STATUS_OK, result.getStatus());
    }

    private UplinkMessage message(int serviceType, String decodedJson, String decodeStatus)
            throws Exception {
        UplinkMessage up = new UplinkMessage();
        up.setDeviceId("GW1");
        up.setServiceType(serviceType);
        up.setDecodeStatus(decodeStatus);
        if (decodedJson != null) {
            up.setServiceDataDecoded(mapper.readTree(decodedJson));
        }
        return up;
    }
}
