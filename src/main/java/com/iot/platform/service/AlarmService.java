package com.iot.platform.service;

import com.iot.platform.model.Alarm;
import com.iot.platform.repository.AlarmRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class AlarmService {

    @Autowired
    private AlarmRepository alarmRepository;

    public List<Alarm> list(String status) {
        return status == null || status.isEmpty()
                ? alarmRepository.findAll()
                : alarmRepository.findByStatus(status);
    }

    public Optional<Alarm> get(String id) {
        return alarmRepository.findById(id);
    }

    public Alarm resolve(String id) {
        Alarm a = alarmRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("alarm not found: " + id));
        a.setStatus("RESOLVED");
        a.setResolvedAt(new Date());
        return alarmRepository.save(a);
    }
}
