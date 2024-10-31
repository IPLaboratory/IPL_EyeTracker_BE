package com.et.server.controller;

import com.et.server.entity.Feature;
import com.et.server.service.FeatureService;
import com.et.server.service.GestureService;
import com.et.server.entity.Gesture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/et/gesture")
public class GestureController {

    @Autowired
    private FeatureService featureService;

    @Autowired
    private GestureService gestureService;

    @Value("${app.upload-dir}")
    private String UPLOAD_DIR;

    @GetMapping("/findAllGestures")
    public ResponseEntity<MultiValueMap<String, Object>> findAllGestures() {
        log.info("전체 제스처 조회 요청");

        List<Gesture> gestures = gestureService.findAllGestures();
        MultiValueMap<String, Object> responseData = new LinkedMultiValueMap<>();

        for (Gesture gesture : gestures) {
            log.info("Gesture Name={}", gesture.getName());

            // 제스처 정보(JSON) 추가
            Map<String, Object> gestureData = createGestureDataMap(gesture);
            HttpHeaders jsonHeaders = new HttpHeaders();
            jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
            responseData.add("gestureData", new ResponseEntity<>(gestureData, jsonHeaders, HttpStatus.OK));

            // 사진 파일 추가
            addPhotoToResponseData(responseData, gesture);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        log.info("전체 제스처 조회 성공");

        return new ResponseEntity<>(responseData, headers, HttpStatus.OK);
    }

    @GetMapping("/isSelected")
    public ResponseEntity<Map<String, Object>> findAllFeatures(@RequestParam Long deviceId) {
        try {
            log.info("제스처 선택 조회 요청: deviceId={}", deviceId);
            List<Feature> features = featureService.findAllFeatures(deviceId);

            if (features.isEmpty()) {
                log.info("기능이 등록되지 않음: deviceId={}", deviceId);
            } else {
                for (Feature feature : features) {
                    String gestureName = (feature.getGesture() != null) ? feature.getGesture().getName() : null;
                    log.info("FeatureId={}, Name={}, GestureName={}", feature.getId(), feature.getName(), gestureName);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", "제스처 선택 조회 성공!");
            response.put("features", features.stream().map(this::featureToMap).toList());

            log.info("제스처 선택 조회 성공: deviceId={}, featureCount={}", deviceId, features.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("제스처 선택 조회 중 오류 발생: {}", e.getMessage());
            return createResponse("제스처 선택 조회 중 오류 발생: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Map<String, Object> featureToMap(Feature feature) {
        Map<String, Object> featureData = new HashMap<>();
        featureData.put("featureId", feature.getId());
        featureData.put("name", feature.getName());
        featureData.put("ir", feature.getIr());

        // 제스처 정보 추가
        if (feature.getGesture() != null) {
            featureData.put("gestureName", feature.getGesture().getName());
        }
        return featureData;
    }

    // 제스처 데이터 맵 생성 메서드
    private Map<String, Object> createGestureDataMap(Gesture gesture) {
        Map<String, Object> gestureData = new HashMap<>();
        gestureData.put("name", gesture.getName());
        gestureData.put("description", gesture.getDescription());
        return gestureData;
    }

    // 사진 파일을 응답 데이터에 추가하는 메서드
    private void addPhotoToResponseData(MultiValueMap<String, Object> responseData, Gesture gesture) {
        String photoFilename = gesture.getPhotoPath();
        if (photoFilename != null && !photoFilename.isEmpty()) {
            File photoFile = new File(UPLOAD_DIR + "/gesture/" + photoFilename);
            if (photoFile.exists()) {
                Resource resource = new FileSystemResource(photoFile);
                HttpHeaders fileHeaders = new HttpHeaders();
                fileHeaders.setContentDispositionFormData("photo", photoFile.getName());
                fileHeaders.setContentType(MediaType.IMAGE_PNG);
                responseData.add("gestureData", new ResponseEntity<>(resource, fileHeaders, HttpStatus.OK));
            } else {
                log.warn("사진 파일을 찾을 수 없음: filename={}", photoFilename);
            }
        }
    }

    private ResponseEntity<Map<String, Object>> createResponse(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", status.value());
        response.put("message", message);
        return new ResponseEntity<>(response, status);
    }
}
