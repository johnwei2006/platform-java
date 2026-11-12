package com.iot.platform.controller;

import com.iot.platform.model.DeviceRealtimeStatus;
import com.iot.platform.realtime.RedisDeviceStatusStore;
import com.iot.platform.security.JwtFilter;
import com.iot.platform.service.DeviceService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest(
        controllers = {DeviceRealtimeController.class, DeviceController.class},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtFilter.class))
public class DeviceRealtimeControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private RedisDeviceStatusStore store;
    @MockBean
    private DeviceService deviceService;

    @Test
    public void listPathWinsOverDevicePathVariable() throws Exception {
        when(store.list()).thenReturn(Collections.singletonList(snapshot("D1")));

        mvc.perform(get("/api/devices/realtime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].deviceId").value("D1"))
                .andExpect(jsonPath("$.data[0].status").value("ONLINE"));

        verify(deviceService, never()).get("realtime");
    }

    @Test
    public void getReturnsSingleDeviceStatus() throws Exception {
        when(store.find("D1")).thenReturn(Optional.of(snapshot("D1")));

        mvc.perform(get("/api/devices/realtime/D1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceId").value("D1"))
                .andExpect(jsonPath("$.data.ttlSeconds").value(120));
    }

    @Test
    public void expiredKeyReturnsNotFoundBusinessCode() throws Exception {
        when(store.find("offline")).thenReturn(Optional.empty());

        mvc.perform(get("/api/devices/realtime/offline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    private DeviceRealtimeStatus snapshot(String deviceId) {
        DeviceRealtimeStatus status = new DeviceRealtimeStatus();
        status.setDeviceId(deviceId);
        status.setStatus("ONLINE");
        status.setTtlSeconds(120);
        return status;
    }
}
