package com.iot.platform.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.config.RealtimeProperties;
import com.iot.platform.model.DeviceRealtimeStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RedisDeviceStatusStoreTest {

    @Mock
    private StringRedisTemplate template;
    @Mock
    private ValueOperations<String, String> valueOps;

    private final ObjectMapper mapper = new ObjectMapper();
    private RealtimeProperties properties;
    private RedisDeviceStatusStore store;

    @Before
    public void setUp() {
        properties = new RealtimeProperties();
        store = new RedisDeviceStatusStore(template, mapper, properties);
    }

    @Test
    public void writesJsonUnderPrefixWithTtl() throws Exception {
        when(template.opsForValue()).thenReturn(valueOps);

        assertTrue(store.write(status("D1")));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("device:status:D1"), json.capture(), eq(120L), eq(TimeUnit.SECONDS));
        DeviceRealtimeStatus parsed = mapper.readValue(json.getValue(), DeviceRealtimeStatus.class);
        assertEquals("D1", parsed.getDeviceId());
        assertEquals("ONLINE", parsed.getStatus());
    }

    @Test
    public void trimsOversizedBusinessDataAndMarksTruncated() throws Exception {
        properties.setMaxJsonBytes(400);
        when(template.opsForValue()).thenReturn(valueOps);
        DeviceRealtimeStatus status = status("D2");
        Map<String, Object> big = new HashMap<String, Object>();
        StringBuilder blob = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            blob.append('x');
        }
        big.put("blob", blob.toString());
        status.setLatestBusinessData(big);

        assertTrue(store.write(status));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("device:status:D2"), json.capture(), eq(120L), eq(TimeUnit.SECONDS));
        assertTrue(json.getValue().getBytes("UTF-8").length <= 400);
        DeviceRealtimeStatus parsed = mapper.readValue(json.getValue(), DeviceRealtimeStatus.class);
        assertEquals("D2", parsed.getDeviceId());
        assertTrue(parsed.isTruncated());
        assertNull(parsed.getLatestBusinessData());
    }

    @Test
    public void refusesWriteWhenJsonCannotFitLimit() {
        properties.setMaxJsonBytes(20);

        assertFalse(store.write(status("D3")));

        verify(valueOps, never()).set(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(),
                any(TimeUnit.class));
    }

    @Test
    public void disabledStoreDoesNotTouchRedis() {
        properties.setEnabled(false);

        assertFalse(store.write(status("D4")));
        assertFalse(store.find("D4").isPresent());
        assertTrue(store.list().isEmpty());

        verifyZeroInteractions(template);
    }

    @Test
    public void missingTemplateDegradesGracefully() {
        RedisDeviceStatusStore noTemplate =
                new RedisDeviceStatusStore((StringRedisTemplate) null, mapper, properties);

        assertFalse(noTemplate.write(status("D5")));
        assertFalse(noTemplate.find("D5").isPresent());
        assertTrue(noTemplate.list().isEmpty());
    }

    @Test
    public void findParsesStoredSnapshot() throws Exception {
        String json = mapper.writeValueAsString(status("D6"));
        when(template.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("device:status:D6")).thenReturn(json);

        DeviceRealtimeStatus found = store.find("D6").get();

        assertEquals("D6", found.getDeviceId());
        assertEquals("ONLINE", found.getStatus());
    }

    @Test
    public void missingKeyMeansOffline() {
        when(template.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("device:status:D7")).thenReturn(null);

        assertFalse(store.find("D7").isPresent());
    }

    @Test
    public void readFailureDegradesToEmpty() {
        when(template.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("device:status:D8")).thenThrow(new RuntimeException("redis down"));

        assertFalse(store.find("D8").isPresent());
    }

    @Test
    public void listUsesScanAndSkipsMalformedJson() throws Exception {
        String valid = mapper.writeValueAsString(status("D9"));
        when(template.execute(ArgumentMatchers.<RedisCallback<List<String>>>any()))
                .thenReturn(Arrays.asList("device:status:D9", "device:status:BAD"));
        when(template.opsForValue()).thenReturn(valueOps);
        when(valueOps.multiGet(ArgumentMatchers.<Collection<String>>any()))
                .thenReturn(Arrays.asList(valid, "not-json"));

        List<DeviceRealtimeStatus> result = store.list();

        assertEquals(1, result.size());
        assertEquals("D9", result.get(0).getDeviceId());
        verify(template, never()).keys(anyString());
    }

    @Test
    public void scanFailureDegradesToEmptyList() {
        when(template.execute(ArgumentMatchers.<RedisCallback<List<String>>>any()))
                .thenThrow(new RuntimeException("redis down"));

        assertTrue(store.list().isEmpty());
    }

    @Test
    public void emptyScanResultNeedsNoValueRead() {
        when(template.execute(ArgumentMatchers.<RedisCallback<List<String>>>any()))
                .thenReturn(Collections.<String>emptyList());

        assertTrue(store.list().isEmpty());
        verifyZeroInteractions(valueOps);
    }

    private DeviceRealtimeStatus status(String deviceId) {
        DeviceRealtimeStatus status = new DeviceRealtimeStatus();
        status.setDeviceId(deviceId);
        status.setStatus("ONLINE");
        status.setLastSeen(1700000000000L);
        status.setUpdatedAt(1700000000000L);
        status.setTtlSeconds(120);
        status.setServiceType(24);
        status.setLatestBusinessData(Collections.<String, Object>singletonMap("channel", 1));
        return status;
    }
}
