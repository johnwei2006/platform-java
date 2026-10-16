package com.iot.platform.controller;

import com.iot.platform.common.ApiResponse;
import com.iot.platform.common.ResultCode;
import com.iot.platform.model.DataRecord;
import com.iot.platform.repository.DataRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/data")
public class DataController {

    @Autowired
    private DataRecordRepository dataRecordRepository;

    @GetMapping
    public ApiResponse<?> query(@RequestParam String deviceId,
                               @RequestParam(required = false) Long from,
                               @RequestParam(required = false) Long to) {
        if (from != null && to != null) {
            List<DataRecord> list = dataRecordRepository.findByDeviceIdAndCreatedAtBetween(
                    deviceId, new Date(from), new Date(to));
            return ApiResponse.success(list);
        }
        return ApiResponse.success(dataRecordRepository.findByDeviceIdOrderByCreatedAtDesc(deviceId));
    }

    @GetMapping("/latest")
    public ApiResponse<?> latest(@RequestParam String deviceId) {
        return dataRecordRepository.findFirstByDeviceIdOrderByCreatedAtDesc(deviceId)
                .map(ApiResponse::success)
                .orElse(ApiResponse.fail(ResultCode.NOT_FOUND));
    }
}
