package com.et.server.service;

import com.et.server.entity.Device;
import com.et.server.entity.Home;
import com.et.server.repository.DeviceRepository;
import com.et.server.repository.HomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly=true)
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final HomeRepository homeRepository;

    // 디바이스 추가
    @Transactional
    public Device addDevice(Long homeId, Device device) {
        Home home = homeRepository.findById(homeId);
        if (home == null) {
            throw new IllegalStateException("홈을 찾을 수 없습니다.");
        }
        device.setHome(home);
        deviceRepository.save(device);
        return device;
    }

    // 디바이스 조회
    public Device findDevice(Long deviceId) {
        Device device = deviceRepository.findById(deviceId);
        if (device == null) {
            throw new IllegalStateException("디바이스를 찾을 수 없습니다.");
        }
        return device;
    }

    // 디바이스 전체 조회
    public List<Device> findAllDevices(Long homeId) {
        return deviceRepository.findAll(homeId);
    }
}
