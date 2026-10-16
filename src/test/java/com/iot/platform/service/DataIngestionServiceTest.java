package com.iot.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.mq.UplinkMessage;
import com.iot.platform.model.Alarm;
import com.iot.platform.model.DataRecord;
import com.iot.platform.model.Device;
import com.iot.platform.repository.AlarmRepository;
import com.iot.platform.repository.DataRecordRepository;
import com.iot.platform.repository.DeviceRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DataIngestionServiceTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private DataRecordRepository dataRecordRepository;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private AlarmRepository alarmRepository;

    @InjectMocks
    private DataIngestionService service;

    @Test
    public void ingestsUplinkAndPersistsRecordAndDevice() throws Exception {
        UplinkMessage up = new UplinkMessage();
        up.setDeviceId("D1");
        up.setServiceType(1);
        up.setServiceVersion(1);
        up.setServiceData("AQID");
        up.setEventId(100);
        byte[] body = objectMapper.writeValueAsBytes(up);
        Message msg = new Message(body, new MessageProperties());

        when(deviceRepository.findByDeviceId("D1")).thenReturn(Optional.empty());
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dataRecordRepository.save(any(DataRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        service.onUplink(msg);

        verify(dataRecordRepository).save(ArgumentMatchers.argThat(
                r -> "D1".equals(r.getDeviceId()) && r.getServiceType() == 1 && r.getEventId() == 100));
        verify(deviceRepository).save(ArgumentMatchers.argThat(
                d -> "ONLINE".equals(d.getStatus()) && d.getLastSeen() != null));
    }

    @Test
    public void ingestsUplinkCreatesAlarmOnServiceType99() throws Exception {
        UplinkMessage up = new UplinkMessage();
        up.setDeviceId("D2");
        up.setServiceType(99);
        up.setServiceVersion(1);
        up.setServiceData("YWJj");
        up.setEventId(200);
        byte[] body = objectMapper.writeValueAsBytes(up);
        Message msg = new Message(body, new MessageProperties());

        when(deviceRepository.findByDeviceId("D2")).thenReturn(Optional.empty());
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dataRecordRepository.save(any(DataRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(alarmRepository.save(any(Alarm.class))).thenAnswer(inv -> inv.getArgument(0));

        service.onUplink(msg);

        verify(alarmRepository).save(ArgumentMatchers.argThat(
                a -> "D2".equals(a.getDeviceId()) && "ERROR".equals(a.getLevel()) && "OPEN".equals(a.getStatus())));
    }

    @Test(expected = RuntimeException.class)
    public void ingestsThrowsOnBadJson() {
        Message msg = new Message("not-json".getBytes(), new MessageProperties());
        service.onUplink(msg);
    }
}
