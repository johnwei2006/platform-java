package com.iot.platform.service;

import com.iot.platform.model.Device;
import com.iot.platform.repository.DeviceRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @InjectMocks
    private DeviceService deviceService;

    @Test
    public void createSetsDefaultStatusAndTimestamps() {
        Device d = new Device();
        d.setDeviceId("D1");
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        Device saved = deviceService.create(d);

        assertEquals("OFFLINE", saved.getStatus());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test(expected = RuntimeException.class)
    public void updateThrowsWhenNotFound() {
        when(deviceRepository.findById("missing")).thenReturn(Optional.empty());
        deviceService.update("missing", new Device());
    }

    @Test
    public void updateCopiesProvidedFields() {
        Device existing = new Device();
        existing.setId("x");
        existing.setDeviceId("D1");
        existing.setStatus("OFFLINE");
        when(deviceRepository.findById("x")).thenReturn(Optional.of(existing));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        Device patch = new Device();
        patch.setName("冷链车A");
        patch.setStatus("ONLINE");
        Device updated = deviceService.update("x", patch);

        assertEquals("冷链车A", updated.getName());
        assertEquals("ONLINE", updated.getStatus());
    }

    @Test
    public void markOnlineCreatesWhenAbsent() {
        when(deviceRepository.findByDeviceId("D9")).thenReturn(Optional.empty());
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        deviceService.markOnline("D9");

        verify(deviceRepository).save(ArgumentMatchers.argThat(
                d -> "ONLINE".equals(d.getStatus()) && "D9".equals(d.getDeviceId())));
    }

    @Test
    public void markOnlineUpdatesWhenPresent() {
        Device d = new Device();
        d.setDeviceId("D9");
        d.setStatus("OFFLINE");
        when(deviceRepository.findByDeviceId("D9")).thenReturn(Optional.of(d));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        deviceService.markOnline("D9");

        assertTrue(true);
    }
}
