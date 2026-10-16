package com.iot.platform.controller;

import com.iot.platform.common.ApiResponse;
import com.iot.platform.mq.DownlinkMessage;
import com.iot.platform.service.DownlinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
public class DownlinkController {

    @Autowired
    private DownlinkService downlinkService;

    @PostMapping("/{id}/downlink")
    public ApiResponse<?> downlink(@PathVariable String id, @RequestBody DownlinkMessage dm) {
        dm.setDeviceId(id);
        downlinkService.send(dm);
        return ApiResponse.success(null);
    }
}
