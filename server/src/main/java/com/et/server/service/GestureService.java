package com.et.server.service;

import com.et.server.Gesture;
import com.et.server.repository.GestureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GestureService {

    private final GestureRepository gestureRepository;

    private Path fileStorageLocation;   // 파일 저장 위치

    @Value("${app.upload-dir}")
    private String uploadDir;

    // 파일 저장 위치를 초기화
    public void initializeFileStorageLocation() {
        // 업로드 디렉토리의 절대 경로를 설정
        this.fileStorageLocation = Paths.get(uploadDir, "gesture").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new IllegalStateException("업로드 파일을 저장할 디렉토리를 생성할 수 없습니다.", ex);
        }
    }

    // 제스처 데이터를 초기화
    @Transactional
    public void initializeGestures() {
        if (gestureRepository.findAll().isEmpty()) {
            gestureRepository.save(new Gesture(null, "up.png", "정면을 보다가 위를 봐주세요"));
            gestureRepository.save(new Gesture(null, "small.png", "정면을 보고 눈을 작게 떠주세요"));
            gestureRepository.save(new Gesture(null, "right.png", "정면을 보다가 오른쪽을 봐주세요"));
            gestureRepository.save(new Gesture(null, "left.png", "정면을 보다가 왼쪽을 봐주세요"));
            gestureRepository.save(new Gesture(null, "down.png", "정면을 보다가 아래를 봐주세요"));
            gestureRepository.save(new Gesture(null, "close.png", "정면을 보고 눈을 감았다 떠주세요"));
            gestureRepository.save(new Gesture(null, "big.png", "정면을 보고 눈을 크게 떠주세요"));
        }
    }

    public List<Gesture> findAllGestures() {
        return gestureRepository.findAll();
    }
}
