package com.iot.platform.controller;

import com.iot.platform.common.ApiResponse;
import com.iot.platform.common.ResultCode;
import com.iot.platform.model.Device;
import com.iot.platform.service.DeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    @Autowired
    private DeviceService deviceService;

    @GetMapping
    public ApiResponse<?> list() {
        return ApiResponse.success(deviceService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable String id) {
        Optional<Device> d = deviceService.get(id);
        return d.isPresent() ? ApiResponse.success(d.get()) : ApiResponse.fail(ResultCode.NOT_FOUND);
    }

    @PostMapping
    public ApiResponse<?> create(@RequestBody Device d) {
        return ApiResponse.success(deviceService.create(d));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody Device d) {
        try {
            return ApiResponse.success(deviceService.update(id, d));
        } catch (RuntimeException e) {
            return ApiResponse.fail(404, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String id) {
        deviceService.delete(id);
        return ApiResponse.success(null);
    }
}
