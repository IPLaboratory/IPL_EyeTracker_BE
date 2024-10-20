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

    private volatile String getIrValue;   // 받아온 IR 값

    @PostMapping("/sendBoolean")
    public ResponseEntity<Map<String, Object>> sendBoolean(@RequestParam boolean value) {
        String arduinoUrl = ARDUINO_URL + "/sendBoolean";

        try {
            // HttpHeaders 생성
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN); // 적절한 Content-Type 설정

            // HttpEntity에 본문과 헤더 설정
            HttpEntity<String> entity = new HttpEntity<>(String.valueOf(value), headers);

            // RestTemplate로 POST 요청 보냄 (헤더 포함)
            new RestTemplate().postForObject(arduinoUrl, entity, String.class);

            synchronized (this) {
                wait(5000); // 최대 5초 대기 (5000)
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", HttpStatus.OK.value());
            response.put("message", value ? "수신부 작동 요청 성공!" : "수신부 작동 요청 실패!");
            response.put("irValue", getIrValue);
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


    @PostMapping("/receiveIr")
    public ResponseEntity<Map<String, Object>> receiveIrValue(@RequestBody Map<String, String> body) {
        String irValue = body.get("irValue");
        synchronized (this) {
            getIrValue = irValue;
            notifyAll(); // 대기 중인 sendBoolean 메서드에 통지
        }
        log.info("IR 값 수신 성공: irValue = {}", irValue);
        return createResponse("IR 수신 성공! ir: " + irValue, HttpStatus.OK);
    }

    @PostMapping("/addFeature")
    public ResponseEntity<Map<String, Object>> addFeature(@RequestParam Long deviceId,
                                                          @RequestParam String name,
                                                          @RequestParam String ir,
                                                          @RequestParam Long gestureId) {
        try {
            log.info("기능 추가 요청: deviceId={}, gestureId={}, name={}", deviceId, gestureId, name);

            Feature feature = new Feature();
            feature.setName(name);
            feature.setIr(ir);
            featureService.addFeature(deviceId, gestureId, feature);

            log.info("기능 추가 성공: featureId={}", feature.getId());
            return createResponse("기능 추가 성공!", HttpStatus.CREATED, feature);
        } catch (DataIntegrityViolationException e) {
            log.warn("이미 사용 중인 제스처로 기능 추가 실패: gestureId={}", gestureId);
            return createConflictResponse("이미 사용 중인 제스처입니다.");
        } catch (IllegalStateException e) {
            log.error("기능 추가 실패: {}", e.getMessage());
            return createResponse("기능 추가 실패: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("기능 추가 중 오류 발생: {}", e.getMessage());
            return createResponse("기능 추가 중 오류 발생: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/findAllFeatures")
    public ResponseEntity<MultiValueMap<String, Object>> findAllFeatures(@RequestParam Long deviceId) {
        log.info("전체 기능 조회 요청: deviceId={}", deviceId);

        List<Feature> features = featureService.findAllFeatures(deviceId);
        MultiValueMap<String, Object> responseData = new LinkedMultiValueMap<>();

        for (Feature feature : features) {
            log.info("FeatureId={}, Name={}, IR={}", feature.getId(), feature.getName(), feature.getIr());

            // 기능 정보(JSON) 추가
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

    // 기능 데이터 맵 생성
    private Map<String, Object> createFeatureDataMap(Feature feature) {
        Map<String, Object> featureData = new HashMap<>();
        featureData.put("featureId", feature.getId());
        featureData.put("name", feature.getName());
        return featureData;
    }

    // 사진 파일을 응답 데이터에 추가
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
                log.info("GestureId={}, {}", gesture.getId(), photoFilename);
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

    // 충돌 응답 생성
    private ResponseEntity<Map<String, Object>> createConflictResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "409");
        response.put("message", message);
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }
}
