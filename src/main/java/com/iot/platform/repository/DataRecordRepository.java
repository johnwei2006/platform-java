package com.iot.platform.repository;

import com.iot.platform.model.DataRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface DataRecordRepository extends MongoRepository<DataRecord, String> {

    List<DataRecord> findByDeviceIdOrderByCreatedAtDesc(String deviceId);

    List<DataRecord> findByDeviceIdAndCreatedAtBetween(String deviceId, Date from, Date to);

    Optional<DataRecord> findFirstByDeviceIdOrderByCreatedAtDesc(String deviceId);
}
