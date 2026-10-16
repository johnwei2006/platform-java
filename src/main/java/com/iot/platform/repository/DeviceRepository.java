package com.iot.platform.repository;

import com.iot.platform.model.Device;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends MongoRepository<Device, String> {

    Optional<Device> findByDeviceId(String deviceId);

    List<Device> findByStatus(String status);
}
