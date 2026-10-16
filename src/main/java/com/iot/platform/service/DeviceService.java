package com.iot.platform.service;

import com.iot.platform.model.Device;
import com.iot.platform.repository.DeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class DeviceService {

    @Autowired
    private DeviceRepository deviceRepository;

    public List<Device> list() {
        return deviceRepository.findAll();
    }

    public Optional<Device> get(String id) {
        return deviceRepository.findById(id);
    }

    public Optional<Device> getByDeviceId(String deviceId) {
        return deviceRepository.findByDeviceId(deviceId);
    }

    public Device create(Device d) {
        Date now = new Date();
        d.setCreatedAt(now);
        d.setUpdatedAt(now);
        if (d.getStatus() == null) {
            d.setStatus("OFFLINE");
        }
        return deviceRepository.save(d);
    }

    public Device update(String id, Device src) {
        Device d = deviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("device not found: " + id));
        if (src.getName() != null) {
            d.setName(src.getName());
        }
        if (src.getNodeId() != null) {
            d.setNodeId(src.getNodeId());
        }
        if (src.getIdentifierType() != null) {
            d.setIdentifierType(src.getIdentifierType());
        }
        if (src.getStatus() != null) {
            d.setStatus(src.getStatus());
        }
        d.setUpdatedAt(new Date());
        return deviceRepository.save(d);
    }

    public void delete(String id) {
        deviceRepository.deleteById(id);
    }

    public void markOnline(String deviceId) {
        Device d = deviceRepository.findByDeviceId(deviceId).orElseGet(() -> {
            Device n = new Device();
            n.setDeviceId(deviceId);
            n.setCreatedAt(new Date());
            return n;
        });
        d.setStatus("ONLINE");
        d.setLastSeen(new Date());
        d.setUpdatedAt(new Date());
        deviceRepository.save(d);
    }
}
