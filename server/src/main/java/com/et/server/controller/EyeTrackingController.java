package com.et.server.controller;

import com.et.server.Device;
import com.et.server.Member;
import com.et.server.repository.MemberRepository;
import com.et.server.repository.DeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
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

    @Value("${app.ml-server-url}")
    private String ML_SERVER_URL;

    private boolean cameraOn;
    private String sendMemberName;

    // 아이트래킹에 필요한 정보들
    private Long MLHomeId;
    private Long MLDeviceId;

    @PostMapping("/memberName")
    public ResponseEntity<Map<String, Object>> memberName(@RequestParam Long homeId,
                                                          @RequestParam Long memberId,
                                                          @RequestParam String memberName) {
        MLHomeId = homeId;
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
    public ResponseEntity<Map<String, Object>> getDeviceName(@RequestParam Long deviceId) {
        Map<String, Object> response = new HashMap<>();
        MLDeviceId = deviceId;

        // deviceId로 디바이스 조회
        Device device = deviceRepository.findById(deviceId);

        if (device == null) {
            log.warn("존재하지 않는 deviceId: {}", deviceId);
            response.put("status", HttpStatus.NOT_FOUND.value());
            response.put("message", "해당 ID의 디바이스가 존재하지 않습니다.");
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }

        // 디바이스 이름 반환
        response.put("status", HttpStatus.OK.value());
        response.put("message", "디바이스 조회 성공");
        response.put("deviceName", device.getName());
        log.info("deviceId {}의 이름: {}", deviceId, device.getName());
        return new ResponseEntity<>(response, HttpStatus.OK);
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

    // ML 서버로 카메라 on/off 요청
    private void sendRequestToMLServer() {
        try {
            // 요청할 URL 생성
            String url = UriComponentsBuilder.fromHttpUrl(ML_SERVER_URL + "/On_Off/")
                    .queryParam("name", sendMemberName)
                    .queryParam("is_camera_on", cameraOn)
                    .encode()
                    .toUriString();

            // GET 요청 전송
            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    String.class
            );

            // 응답 처리
            log.info("ML 서버 응답 상태 코드: {}", responseEntity.getStatusCode());
            log.info("ML 서버 응답 본문: {}", responseEntity.getBody());
        } catch (Exception e) {
            log.error("ML 서버와의 통신 중 오류 발생", e);
            throw new RuntimeException("ML 서버와의 통신 실패");
        }
    }

    // 공통 응답 생성 메서드
    private ResponseEntity<Map<String, Object>> createResponse(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", status.value());
        response.put("message", message);
        return new ResponseEntity<>(response, status);
    }
}
