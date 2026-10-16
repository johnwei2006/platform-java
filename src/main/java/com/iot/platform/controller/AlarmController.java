package com.iot.platform.controller;

import com.iot.platform.common.ApiResponse;
import com.iot.platform.common.ResultCode;
import com.iot.platform.model.Alarm;
import com.iot.platform.service.AlarmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alarms")
public class AlarmController {

    @Autowired
    private AlarmService alarmService;

    @GetMapping
    public ApiResponse<?> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(alarmService.list(status));
    }

    @PostMapping("/{id}/resolve")
    public ApiResponse<?> resolve(@PathVariable String id) {
        try {
            return ApiResponse.success(alarmService.resolve(id));
        } catch (RuntimeException e) {
            return ApiResponse.fail(ResultCode.NOT_FOUND);
        }
    }
}
