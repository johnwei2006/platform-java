package com.iot.platform.controller;

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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest(
        controllers = DeviceController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtFilter.class))
public class DeviceControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private DeviceService deviceService;

    @Test
    public void listReturnsOk() throws Exception {
        when(deviceService.list()).thenReturn(Collections.emptyList());
        mvc.perform(get("/api/devices")).andExpect(status().isOk());
    }

    @Test
    public void getMissingReturnsNotFoundBody() throws Exception {
        when(deviceService.get("missing")).thenReturn(java.util.Optional.empty());
        mvc.perform(get("/api/devices/missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }
}
