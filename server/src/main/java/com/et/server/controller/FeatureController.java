package com.et.server.controller;

import com.et.server.Feature;
import com.et.server.Gesture;
import com.et.server.service.FeatureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/et/feature")
public class FeatureController {

    @Autowired
    private FeatureService featureService;

    @Value("${app.arduino-url}")
    private String ARDUINO_URL;

    @Value("${app.upload-dir}")
    private String UPLOAD_DIR;

    private volatile String getIrValue;     // 수신된 IR 값을 저장하는 변수

    // 아두이노 서버에 Boolean 값을 보내는 메서드
    @PostMapping("/sendBoolean")
    public ResponseEntity<Map<String, Object>> sendBoolean(@RequestParam boolean value) {
        String arduinoUrl = ARDUINO_URL + "/sendBoolean";

        try {
            // HTTP 요청 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);

            // HTTP 요청 엔터티 생성 및 요청 전송
            HttpEntity<String> entity = new HttpEntity<>(String.valueOf(value), headers);
            new RestTemplate().postForObject(arduinoUrl, entity, String.class);

            // 아두이노의 응답 대기
            synchronized (this) {
                wait(5000); // 최대 대기 시간 5초
            }

            // 응답 데이터 구성 및 반환
            Map<String, Object> response = new HashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", value ? "수신부 작동 요청 성공!" : "수신부 작동 요청 실패!");
            response.put("irValue", getIrValue);
            getIrValue = null;
            response.put("booleanValue", false);
            return ResponseEntity.ok(response);
        } catch (InterruptedException e) {
            log.error("IR 값 수신 중 인터럽트 발생: {}", e.getMessage());
            return createResponse("IR 값 수신 중 인터럽트 발생: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (RestClientException e) {
            log.error("아두이노 서버 요청 중 오류 발생: {}", e.getMessage());
            return createResponse("아두이노 서버 요청 중 오류 발생: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // 아두이노에서 수신된 IR 값을 처리하는 메서드
    @PostMapping("/receiveIr")
    public ResponseEntity<Map<String, Object>> receiveIrValue(@RequestBody Map<String, String> body) {
        String irValue = body.get("irValue");
        synchronized (this) {
            getIrValue = irValue;
            notifyAll();    // 대기 중인 sendBoolean 메서드에 알림
        }
        log.info("IR 값 수신 성공: irValue = {}", irValue);
        return createResponse("IR 수신 성공! ir: " + irValue, HttpStatus.OK);
    }

    // 새로운 Feature(기능)를 추가하는 메서드
    @PostMapping("/addFeature")
    public ResponseEntity<Map<String, Object>> addFeature(@RequestParam Long deviceId,
                                                          @RequestParam String name,
                                                          @RequestParam String ir,
                                                          @RequestParam String gestureName) {
        try {
            log.info("기능 추가 요청: deviceId={}, gestureName={}, name={}", deviceId, gestureName, name);

            // 중복 확인: 같은 deviceId와 gestureName이 이미 등록되어 있는지 확인
            List<Feature> existingFeatures = featureService.findAllFeatures(deviceId);
            boolean isDuplicate = existingFeatures.stream()
                    .anyMatch(feature -> feature.getGesture() != null && feature.getGesture().getName().equals(gestureName));

            if (isDuplicate) {
                log.warn("기능 추가 실패 - 이미 동일한 deviceId와 gestureName 조합이 존재합니다: deviceId={}, gestureName={}", deviceId, gestureName);
                return createConflictResponse("해당 제스처는 이미 이 기기에 등록되어 있습니다.");
            }

            Feature feature = new Feature();
            feature.setName(name);
            feature.setIr(ir);
            featureService.addFeature(deviceId, gestureName, feature);

            log.info("기능 추가 성공: featureId={}", feature.getId());
            return createResponse("기능 추가 성공!", HttpStatus.CREATED, feature);
        } catch (DataIntegrityViolationException e) {
            log.warn("이미 사용 중인 제스처로 기능 추가 실패: gestureName={}", gestureName);
            return createConflictResponse("이미 사용 중인 제스처입니다.");
        } catch (IllegalStateException e) {
            log.error("기능 추가 실패: {}", e.getMessage());
            return createResponse("기능 추가 실패: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("기능 추가 중 오류 발생: {}", e.getMessage());
            return createResponse("기능 추가 중 오류 발생: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // 특정 기기의 모든 Feature(기능) 조회
    @GetMapping("/findAllFeatures")
    public ResponseEntity<MultiValueMap<String, Object>> findAllFeatures(@RequestParam Long deviceId) {
        log.info("전체 기능 조회 요청: deviceId={}", deviceId);

        List<Feature> features = featureService.findAllFeatures(deviceId);
        MultiValueMap<String, Object> responseData = new LinkedMultiValueMap<>();

        for (Feature feature : features) {
            log.info("FeatureId={}, Name={}, IR={}", feature.getId(), feature.getName(), feature.getIr());

            // Feature 데이터를 JSON 형태로 추가
            Map<String, Object> featureData = createFeatureDataMap(feature);
            HttpHeaders jsonHeaders = new HttpHeaders();
            jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
            responseData.add("featureData", new ResponseEntity<>(featureData, jsonHeaders, HttpStatus.OK));

            // 제스처의 사진 파일 추가
            if (feature.getGesture() != null) {
                addPhotoToResponseData(responseData, feature.getGesture());
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        log.info("전체 기능 조회 성공: deviceId={}, featureCount={}", deviceId, features.size());

        return new ResponseEntity<>(responseData, headers, HttpStatus.OK);
    }

    // Feature 데이터 맵 생성
    private Map<String, Object> createFeatureDataMap(Feature feature) {
        Map<String, Object> featureData = new HashMap<>();
        featureData.put("featureId", feature.getId());
        featureData.put("name", feature.getName());
        return featureData;
    }

    // 제스처의 사진 파일을 응답 데이터에 추가
    private void addPhotoToResponseData(MultiValueMap<String, Object> responseData, Gesture gesture) {
        String photoFilename = gesture.getPhotoPath();
        if (photoFilename != null && !photoFilename.isEmpty()) {
            File photoFile = new File(UPLOAD_DIR + "/gesture/" + photoFilename);
            if (photoFile.exists()) {
                Resource resource = new FileSystemResource(photoFile);
                HttpHeaders fileHeaders = new HttpHeaders();
                fileHeaders.setContentDispositionFormData("photo", photoFile.getName());
                fileHeaders.setContentType(MediaType.IMAGE_PNG);
                responseData.add("featureData", new ResponseEntity<>(resource, fileHeaders, HttpStatus.OK));
                log.info("Gesture : {}, {}", gesture.getName(), photoFilename);
            }
        }
    }

    // 응답 생성 (기능 추가 시)
    private ResponseEntity<Map<String, Object>> createResponse(String message, HttpStatus status, Feature feature) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", status.value());
        response.put("message", message);
        response.put("data", createFeatureDataMap(feature));
        return new ResponseEntity<>(response, status);
    }

    // 일반 응답 생성
    private ResponseEntity<Map<String, Object>> createResponse(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", status.value());
        response.put("message", message);
        return new ResponseEntity<>(response, status);
    }

    // 중복 충돌 응답 생성
    private ResponseEntity<Map<String, Object>> createConflictResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "409");
        response.put("message", message);
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }
}
