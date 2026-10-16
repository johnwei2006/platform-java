package com.iot.platform.repository;

import com.iot.platform.model.Alarm;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AlarmRepository extends MongoRepository<Alarm, String> {

    List<Alarm> findByStatus(String status);

    List<Alarm> findByDeviceIdOrderByCreatedAtDesc(String deviceId);
}
