package com.iot.platform.service;

import com.iot.platform.model.Alarm;
import com.iot.platform.repository.AlarmRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AlarmServiceTest {

    @Mock
    private AlarmRepository alarmRepository;

    @InjectMocks
    private AlarmService alarmService;

    @Test
    public void resolveSetsResolvedAndTimestamp() {
        Alarm a = new Alarm();
        a.setId("a1");
        a.setStatus("OPEN");
        when(alarmRepository.findById("a1")).thenReturn(Optional.of(a));
        when(alarmRepository.save(any(Alarm.class))).thenAnswer(inv -> inv.getArgument(0));

        Alarm r = alarmService.resolve("a1");

        assertEquals("RESOLVED", r.getStatus());
        assertNotNull(r.getResolvedAt());
    }

    @Test(expected = RuntimeException.class)
    public void resolveThrowsWhenNotFound() {
        when(alarmRepository.findById("missing")).thenReturn(Optional.empty());
        alarmService.resolve("missing");
    }

    @Test
    public void listReturnsEmptyWhenNoStatus() {
        when(alarmRepository.findAll()).thenReturn(Collections.emptyList());
        assertTrue(alarmService.list(null).isEmpty());
    }
}
