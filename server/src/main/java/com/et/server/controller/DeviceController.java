package com.et.server.controller;

import com.et.server.entity.Device;
import com.et.server.service.DeviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/et/device")
public class DeviceController {

    @Autowired
    private DeviceService deviceService;

    @Value("${app.upload-dir}")
    private String UPLOAD_DIR;

    @PostMapping("/addDevice")
    public ResponseEntity<Map<String, Object>> addDevice(@RequestParam Long homeId,
                                                         @RequestParam String name,
                                                         @RequestParam(required = false) MultipartFile photo) throws IOException {
        log.info("기기 추가 요청: homeId={}, name={}", homeId, name);
        String photoFilename = null;
        if (photo != null && !photo.isEmpty()) {
            photoFilename = saveFile(photo, homeId, name);
        }

        Device device = new Device();
        device.setName(name);
        device.setPhotoPath(photoFilename);

        Device newDevice = deviceService.addDevice(homeId, device);
        log.info("기기 추가 성공: {}", newDevice);
        return createResponse("기기 추가 성공!", newDevice, homeId);
    }

    @GetMapping("/findAllDevices")
    public ResponseEntity<MultiValueMap<String, Object>> findAllDevices(@RequestParam Long homeId) throws IOException {
        log.info("전체 기기 조회 요청: homeId={}", homeId);
        List<Device> devices = deviceService.findAllDevices(homeId);

        MultiValueMap<String, Object> responseData = new LinkedMultiValueMap<>();
        for (Device device : devices) {
            log.info("deviceId={}, name={}", device.getId(), device.getName());

            // 장치 정보(JSON) 추가
            HttpHeaders jsonHeaders = new HttpHeaders();
            jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> deviceData = new HashMap<>();
            deviceData.put("deviceId", device.getId());
            deviceData.put("name", device.getName());

            responseData.add("deviceData", new HttpEntity<>(deviceData, jsonHeaders));

            // 사진 파일 추가
            String photoFilename = device.getPhotoPath();
            if (photoFilename != null && !photoFilename.isEmpty()) {
                File photoFile = new File(UPLOAD_DIR + "/device/" + photoFilename);
                if (photoFile.exists()) {
                    Resource resource = new org.springframework.core.io.FileSystemResource(photoFile);
                    HttpHeaders fileHeaders = new HttpHeaders();
                    fileHeaders.setContentDispositionFormData("photo", photoFile.getName());
                    fileHeaders.setContentType(MediaType.IMAGE_PNG);
                    responseData.add("deviceData", new HttpEntity<>(resource, fileHeaders));
                }
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        log.info("전체 기기 조회 성공: homeId={}", homeId);

        return new ResponseEntity<>(responseData, headers, HttpStatus.OK);
    }

    // 파일 저장 로직
    private String saveFile(MultipartFile file, Long homeId, String name) throws IOException {
        if (file.isEmpty()) {
            log.error("빈 파일 업로드 시도됨");
            throw new IllegalStateException("빈 파일입니다.");
        }

        String originalFilename = file.getOriginalFilename();
        String fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String baseFileName = date + "_home" + homeId + "_" + name;
        String uniqueFileName = baseFileName + fileExtension;

        // 파일 저장 경로 설정
        String deviceUploadDir = UPLOAD_DIR + "/device";
        File dir = new File(deviceUploadDir);
        if (!dir.exists() && !dir.mkdirs()) {
            log.error("디렉토리 생성 실패: {}", deviceUploadDir);
            throw new IOException("디렉토리 생성에 실패했습니다: " + deviceUploadDir);
        }
        File dest = new File(deviceUploadDir, uniqueFileName);

        // 동일한 파일 이름이 존재할 경우, 고유한 접미사 추가
        int counter = 2;
        while (dest.exists()) {
            uniqueFileName = baseFileName + "_" + counter + fileExtension;
            dest = new File(deviceUploadDir, uniqueFileName);
            counter++;
        }
        file.transferTo(dest);
        return uniqueFileName; // 파일명만 반환
    }

    // 응답 생성
    private ResponseEntity<Map<String, Object>> createResponse(String message, Object data, Long homeId) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "200");
        response.put("message", message);
        response.put("data", data);
        response.put("homeId", homeId);
        return ResponseEntity.ok(response);
    }
}
