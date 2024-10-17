package com.et.server.service;

import com.et.server.Feature;
import com.et.server.repository.FeatureRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FeatureLoader implements CommandLineRunner {

    @Autowired
    private FeatureRepository featureRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // 초기 정보 저장 -> 앱 실행 시, ir값을 라베파로 전송
        if (featureRepository.findAll().isEmpty()) {
            featureRepository.save(new Feature(null, null, null, "1", "외부입력"));
            featureRepository.save(new Feature(null, null, null, "2", "방향키"));
            featureRepository.save(new Feature(null, null, null, "3", "확인"));
        }
    }
}
