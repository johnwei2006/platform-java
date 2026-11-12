package com.iot.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.mq.UplinkMessage;
import com.iot.platform.model.Alarm;
import com.iot.platform.model.DataRecord;
import com.iot.platform.model.Device;
import com.iot.platform.repository.AlarmRepository;
import com.iot.platform.repository.DataRecordRepository;
import com.iot.platform.repository.DeviceRepository;
import com.iot.platform.realtime.DeviceRealtimeStatusService;
import com.iot.platform.service.converter.UplinkBusinessDispatcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
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
    @Mock
    private UplinkBusinessDispatcher uplinkBusinessDispatcher;
    @Mock
    private DeviceRealtimeStatusService realtimeStatusService;

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

    @Test
    public void persistsDtmDecodedStructuredFields() {
        String json = "{\"deviceId\":\"D3\",\"serviceType\":101,\"serviceVersion\":1,"
                + "\"serviceData\":\"AQI=\",\"eventId\":300,"
                + "\"serviceDataDecoded\":{\"appType\":1,\"action\":2},"
                + "\"protocolName\":\"GATEWAY_REBOOT\",\"transactionType\":\"downlink\","
                + "\"decodeStatus\":\"OK\"}";
        Message msg = new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());

        when(deviceRepository.findByDeviceId("D3")).thenReturn(Optional.empty());
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dataRecordRepository.save(any(DataRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        service.onUplink(msg);

        verify(dataRecordRepository).save(ArgumentMatchers.argThat(
                r -> "GATEWAY_REBOOT".equals(r.getProtocolName())
                        && "OK".equals(r.getDecodeStatus())
                        && "downlink".equals(r.getTransactionType())
                        && r.getServiceDataDecoded() != null
                        && Integer.valueOf(1).equals(r.getServiceDataDecoded().get("appType"))
                        && Integer.valueOf(2).equals(r.getServiceDataDecoded().get("action"))
                        && "AQI=".equals(r.getServiceData())));
    }

    @Test
    public void dispatchesBusinessConversionAfterPersisting() throws Exception {
        UplinkMessage up = new UplinkMessage();
        up.setDeviceId("D4");
        up.setServiceType(24);
        up.setServiceVersion(1);
        up.setServiceData("AQ==");
        up.setEventId(400);
        byte[] body = objectMapper.writeValueAsBytes(up);
        Message msg = new Message(body, new MessageProperties());

        when(deviceRepository.findByDeviceId("D4")).thenReturn(Optional.empty());
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dataRecordRepository.save(any(DataRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        service.onUplink(msg);

        verify(uplinkBusinessDispatcher).dispatch(ArgumentMatchers.argThat(
                (UplinkMessage m) -> "D4".equals(m.getDeviceId()) && m.getServiceType() == 24));
    }

    @Test
    public void dispatchFailureDoesNotBreakUplinkAck() throws Exception {
        UplinkMessage up = new UplinkMessage();
        up.setDeviceId("D5");
        up.setServiceType(24);
        up.setEventId(500);
        byte[] body = objectMapper.writeValueAsBytes(up);
        Message msg = new Message(body, new MessageProperties());

        when(deviceRepository.findByDeviceId("D5")).thenReturn(Optional.empty());
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dataRecordRepository.save(any(DataRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("writer down"))
                .when(uplinkBusinessDispatcher).dispatch(any(UplinkMessage.class));

        service.onUplink(msg);

        verify(dataRecordRepository).save(any(DataRecord.class));
        verify(deviceRepository).save(any(Device.class));
    }

    @Test
    public void refreshesRealtimeStatusAfterPersisting() throws Exception {
        UplinkMessage up = new UplinkMessage();
        up.setDeviceId("D6");
        up.setServiceType(1);
        up.setEventId(600);
        Message msg = new Message(objectMapper.writeValueAsBytes(up), new MessageProperties());

        when(deviceRepository.findByDeviceId("D6")).thenReturn(Optional.empty());
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dataRecordRepository.save(any(DataRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        service.onUplink(msg);

        verify(realtimeStatusService).refresh(ArgumentMatchers.argThat(
                (UplinkMessage m) -> "D6".equals(m.getDeviceId()) && m.getServiceType() == 1));
    }

    @Test
    public void realtimeFailureDoesNotBreakUplinkAck() throws Exception {
        UplinkMessage up = new UplinkMessage();
        up.setDeviceId("D7");
        up.setServiceType(1);
        up.setEventId(700);
        Message msg = new Message(objectMapper.writeValueAsBytes(up), new MessageProperties());

        when(deviceRepository.findByDeviceId("D7")).thenReturn(Optional.empty());
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dataRecordRepository.save(any(DataRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("redis down"))
                .when(realtimeStatusService).refresh(any(UplinkMessage.class));

        service.onUplink(msg);

        verify(dataRecordRepository).save(any(DataRecord.class));
        verify(deviceRepository).save(any(Device.class));
    }

    @Test(expected = RuntimeException.class)
    public void ingestsThrowsOnBadJson() {
        Message msg = new Message("not-json".getBytes(), new MessageProperties());
        service.onUplink(msg);
    }
}
