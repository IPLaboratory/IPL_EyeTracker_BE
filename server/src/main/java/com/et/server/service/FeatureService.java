package com.et.server.service;

import com.et.server.entity.Device;
import com.et.server.entity.Feature;
import com.et.server.entity.Gesture;
import com.et.server.repository.DeviceRepository;
import com.et.server.repository.FeatureRepository;
import com.et.server.repository.GestureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FeatureService {

    private final FeatureRepository featureRepository;
    private final DeviceRepository deviceRepository;
    private final GestureRepository gestureRepository;

    // 기능 추가
    @Transactional
    public void addFeature(Long deviceId, String gestureName, Feature feature) {
        // Device 조회
        Device device = deviceRepository.findById(deviceId);
        if (device == null) {
            throw new IllegalStateException("기기를 찾을 수 없습니다.");
        }

        // Gesture 조회
        Gesture gesture = gestureRepository.findByName(gestureName);
        if (gesture == null) {
            throw new IllegalStateException("제스처를 찾을 수 없습니다.");
        }

        // Feature에 Device와 Gesture 설정
        feature.setDevice(device);
        feature.setGesture(gesture);

        // Feature 저장
        featureRepository.save(feature);
    }

    // 기능 삭제
    @Transactional
    public void removeFeature(Long featureId) {
        Feature feature = featureRepository.findById(featureId);
        if (feature == null) {
            throw new IllegalStateException("해당 기능을 찾을 수 없습니다.");
        }

        // 기능을 삭제
        featureRepository.delete(feature);
    }

    // 기능 조회
    public Feature findFeature(Long featureId) {
        Feature feature = featureRepository.findById(featureId);
        if (feature == null) {
            throw new IllegalStateException("해당 기능을 찾을 수 없습니다.");
        }
        return feature;
    }

    // 특정 기기의 모든 기능 조회
    public List<Feature> findAllFeatures(Long deviceId) {
        return featureRepository.findAllDeviceId(deviceId);
    }
}
