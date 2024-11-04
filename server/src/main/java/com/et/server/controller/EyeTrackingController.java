package com.et.server.controller;

import com.et.server.entity.Device;
import com.et.server.entity.Feature;
import com.et.server.entity.Member;
import com.et.server.repository.MemberRepository;
import com.et.server.repository.DeviceRepository;
import com.et.server.service.FeatureService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/et/eyeTracking")
public class EyeTrackingController {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private FeatureService featureService;

    @Value("${app.ml-server-url}")
    private String ML_SERVER_URL;

    @Value("${app.arduino-url}")
    private String ARDUINO_URL;

    private boolean cameraOn;
    private String sendMemberName;

    // 아이트래킹에 필요한 정보
    private Long MLDeviceId;

    @PostMapping("/memberName")
    public ResponseEntity<Map<String, Object>> memberName(@RequestParam Long homeId,
                                                          @RequestParam Long memberId,
                                                          @RequestParam String memberName) {
        Map<String, Object> response = new HashMap<>();

        // 파라미터 값 로그 출력
        log.info("요청된 파라미터 - homeId: {}, memberId: {}, memberName: {}", homeId, memberId, memberName);

        // memberId로 멤버 조회
        Member member = memberRepository.findById(memberId);

        if (member == null) {
            log.warn("존재하지 않는 memberId: {}", memberId);
            response.put("status", HttpStatus.NOT_FOUND.value());
            response.put("message", "해당 ID의 멤버가 존재하지 않습니다.");
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        } else if (!member.getName().equals(memberName)) {
            log.warn("memberId {}에 대한 이름이 일치하지 않음: 입력된 이름 = {}, 저장된 이름 = {}", memberId, memberName, member.getName());
            response.put("status", HttpStatus.BAD_REQUEST.value());
            response.put("message", "이름이 일치하지 않습니다.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        log.info("memberId {}와 이름이 일치: {}", memberId, memberName);
        sendMemberName = memberName;
        response.put("status", HttpStatus.OK.value());
        response.put("message", "수신 성공하였습니다.");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> eyeTrackingStart() {
        log.info("아이트래킹 시작 요청");

        try {
            cameraOn = true;
            String message = "아이트래킹을 시작합니다.";

            sendRequestToMLServer();

            return createResponse(message, HttpStatus.OK);
        } catch (Exception e) {
            log.error("아이트래킹 시작 중 오류 발생: ", e);
            return createResponse("아이트래킹 시작 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/getDevice")
    public ResponseEntity<Map<String, Object>> getDevice(@RequestParam String deviceName) {
        Map<String, Object> response = new HashMap<>();

        // deviceName으로 디바이스 조회
        Device device = deviceRepository.findByName(deviceName);

        if (device == null) {
            log.warn("존재하지 않는 deviceName: {}", deviceName);
            response.put("status", HttpStatus.NOT_FOUND.value());
            response.put("message", "해당 이름의 디바이스가 존재하지 않습니다.");
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }

        // 디바이스 ID 설정 및 반환
        MLDeviceId = device.getId();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "디바이스 조회 성공");
        response.put("deviceId", device.getId());
        response.put("deviceName", device.getName());

        log.info("ML 객체 탐지 성공 - deviceId: {}, {}", device.getId(), deviceName);

        // MLDeviceId에 매핑된 모든 Feature 조회
        List<Feature> features = featureService.findAllFeatures(MLDeviceId);

        if (features.isEmpty()) {
            log.info("기기에 매핑된 기능이 없습니다: deviceId={}", MLDeviceId);
            response.put("status", HttpStatus.NOT_FOUND.value());
            response.put("message", "해당 기기에 매핑된 기능이 없습니다.");
        } else {
            // 매핑된 Feature와 gestureName 반환을 위한 Map 생성
            List<Map<String, Object>> featureList = features.stream()
                    .map(this::featureToMap) // featureToMap 메서드로 Feature 데이터를 Map으로 변환
                    .toList();

            response.put("features", featureList);
            log.info("기기에 매핑된 기능 및 제스처 조회 성공 - {}, {}, featureCount: {}", MLDeviceId, deviceName, features.size());
        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/getGesture")
    public ResponseEntity<Map<String, Object>> getGesture(@RequestParam String gestureName) {
        Map<String, Object> response = new HashMap<>();

        if (MLDeviceId == null) {
            log.warn("MLDeviceId가 설정되지 않았습니다.");
            response.put("status", HttpStatus.BAD_REQUEST.value());
            response.put("message", "MLDeviceId가 설정되지 않았습니다. 먼저 getDevice를 호출하세요.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        // MLDeviceId에 매핑된 모든 Feature 조회
        List<Feature> features = featureService.findAllFeatures(MLDeviceId);

        if (features.isEmpty()) {
            log.info("기기에 매핑된 기능이 없습니다: deviceId={}", MLDeviceId);
            response.put("status", HttpStatus.NOT_FOUND.value());
            response.put("message", "해당 기기에 매핑된 기능이 없습니다.");
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }

        // 입력받은 gestureName과 일치하는 Feature 필터링
        Feature matchingFeature = features.stream()
                .filter(feature -> feature.getGesture() != null && gestureName.equals(feature.getGesture().getName()))
                .findFirst()
                .orElse(null);

        if (matchingFeature == null) {
            log.info("일치하는 제스처가 없습니다: gestureName={}", gestureName);
            response.put("status", HttpStatus.NOT_FOUND.value());
            response.put("message", "해당 제스처 이름과 일치하는 기능이 없습니다.");
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }

        // 아두이노 서버에 IR 값 전송
        String irValue = matchingFeature.getIr();
        log.info("아두이노 서버로 전송 : {}, {}", gestureName, irValue);

        String arduinoResponse = sendIrToArduino(irValue);
        if ("success".equals(arduinoResponse)) {
            // 성공 시 응답에 Feature 정보 추가
            response.put("status", HttpStatus.OK.value());
            response.put("message", "기능 조회 및 IR 전송 성공");
            response.put("feature", featureToMap(matchingFeature));
            log.info("기능 조회 및 IR 전송 성공 - deviceId: {}, gestureName: {}", MLDeviceId, gestureName);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } else {
            // 실패 시 제어 실패 메시지 응답
            log.error("아두이노 서버 제어 실패 - 응답: {}", arduinoResponse);
            response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "아두이노 서버 제어 실패");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // 아두이노 서버로 IR 값을 전송하고 응답을 받는 메서드
    private String sendIrToArduino(String irValue) {
        String arduinoUrl = ARDUINO_URL + "/sendIR";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);

            HttpEntity<String> entity = new HttpEntity<>(irValue, headers);
            log.info("아두이노 서버에 IR 값 요청: URL={}, IR 값={}", arduinoUrl, irValue); // 아두이노 서버 요청 전 로그

            // 아두이노 서버에 POST 요청 전송
            ResponseEntity<String> response = restTemplate.postForEntity(arduinoUrl, entity, String.class);

            // 아두이노 서버 응답 처리
            if (response.getStatusCode() == HttpStatus.OK) {
                // 응답 본문을 JSON으로 파싱
                ObjectMapper mapper = new ObjectMapper();
                JsonNode responseBody = mapper.readTree(response.getBody());
                int status = responseBody.path("status").asInt();
                String message = responseBody.path("message").asText();

                log.info("아두이노 서버 응답 성공: status={}, message={}", status, message); // 성공 응답 로그

                // status가 200이면 성공으로 처리
                return (status == 200) ? "success" : "fail";
            } else {
                log.error("아두이노 서버로부터 예상치 못한 응답 코드: {}", response.getStatusCode());
                return "error";
            }
        } catch (Exception e) {
            log.error("아두이노 서버로 IR 전송 중 오류 발생: {}", e.getMessage());
            return "error";
        }
    }

    private Map<String, Object> featureToMap(Feature feature) {
        Map<String, Object> featureData = new HashMap<>();
        featureData.put("featureId", feature.getId());
        featureData.put("name", feature.getName());
        featureData.put("ir", feature.getIr());

        if (feature.getGesture() != null) {
            featureData.put("gestureName", feature.getGesture().getName());
            featureData.put("description", feature.getGesture().getDescription());
        }

        return featureData;
    }

    @PostMapping("/end")
    public ResponseEntity<Map<String, Object>> eyeTrackingEnd() {
        log.info("아이트래킹 종료 요청");

        try {
            cameraOn = false;
            String message = "아이트래킹을 종료합니다.";

            sendRequestToMLServer();

            return createResponse(message, HttpStatus.OK);
        } catch (Exception e) {
            log.error("아이트래킹 종료 중 오류 발생: ", e);
            return createResponse("아이트래킹 종료 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void sendRequestToMLServer() {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(ML_SERVER_URL + "/On_Off/")
                    .queryParam("name", sendMemberName)
                    .queryParam("is_camera_on", cameraOn)
                    .encode()
                    .toUriString();

            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    String.class
            );

            log.info("ML 서버 응답 상태 코드: {}", responseEntity.getStatusCode());
            log.info("ML 서버 응답 본문: {}", responseEntity.getBody());
        } catch (Exception e) {
            log.error("ML 서버와의 통신 중 오류 발생", e);
            throw new RuntimeException("ML 서버와의 통신 실패");
        }
    }

    private ResponseEntity<Map<String, Object>> createResponse(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", status.value());
        response.put("message", message);
        return new ResponseEntity<>(response, status);
    }
}
