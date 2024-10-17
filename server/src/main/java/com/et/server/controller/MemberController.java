package com.et.server.controller;

import com.et.server.Member;
import com.et.server.service.MemberService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/et/member")
public class MemberController {

    @Autowired
    private MemberService memberService;

    @Value("${app.upload-dir}")
    private String UPLOAD_DIR;

    private final Map<Long, StringBuilder> uploadSessions = new HashMap<>();
    private final Map<Long, Integer> uploadedChunks = new HashMap<>();

    @PostMapping("/addMember")
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam Long homeId,
                                                          @RequestParam String name,
                                                          @RequestParam String videoChunk,
                                                          @RequestParam Integer chunkIndex,
                                                          @RequestParam Integer totalChunks) throws IOException {

        // 서버에 영상 저장
        handleChunkedUpload(homeId, videoChunk, chunkIndex, totalChunks);

        // ML 서버 통신
        String mlResponse = memberService.sendVideoToMLUpload(name, videoChunk, chunkIndex, totalChunks - 1);
        log.info("ML 서버 응답: {}", mlResponse);

        if (uploadedChunks.get(homeId).equals(totalChunks)) {
            // 모든 영상 조각이 서버에 저장되었을 때
            String base64Data = uploadSessions.remove(homeId).toString();
            uploadedChunks.remove(homeId);
            String videoFilename = saveBase64EncodedFile(base64Data, homeId, "video");

            // 사용자 정보 저장
            Member member = new Member();
            member.setName(name);
            member.setVideoPath(videoFilename);

            Member newMember = memberService.addMember(homeId, member);
            log.info("모든 영상 조각 수신 완료, 사용자 추가 성공: {}", newMember);

            return createResponse("사용자 추가 성공! 영상 처리 완료.", newMember, homeId);
        } else {
            return createResponse("영상 조각 업로드 성공", null, homeId);
        }
    }

    @PostMapping("/addVideo")
    public ResponseEntity<Map<String, Object>> addVideo(@RequestParam Long memberId,
                                                        @RequestParam String videoChunk,
                                                        @RequestParam Integer chunkIndex,
                                                        @RequestParam Integer totalChunks) throws IOException {
        log.info("영상 추가 요청: memberId={}", memberId);

        // memberId로 사용자 정보를 조회하여 이름 가져오기
        Member member = memberService.findMember(memberId);
        String name = member.getName();
        log.info("사용자 이름: {}", name);
        handleChunkedUpload(memberId, videoChunk, chunkIndex, totalChunks);

        // ML 서버 통신
        String mlResponse = memberService.sendVideoToMLAdd(name, videoChunk, chunkIndex, totalChunks - 1);
        log.info("ML 서버 응답: {}", mlResponse);

        if (uploadedChunks.get(memberId).equals(totalChunks)) {
            // 모든 영상 조각이 서버에 저장되었을 때
            String base64Data = uploadSessions.remove(memberId).toString();
            uploadedChunks.remove(memberId);
            String videoFilename2 = saveBase64EncodedFile(base64Data, memberId, "video2");

            // 사용자의 두 번째 영상 경로 업데이트
            member.setVideoPath2(videoFilename2);
            Member updatedMember = memberService.updateMember(memberId, member.getName(), null, videoFilename2);
            log.info("영상 추가 성공: {}", updatedMember);
            return createResponse("동영상 추가 성공!", updatedMember);
        } else {
            return createResponse("영상 조각 업로드 성공", null);
        }
    }

    @PutMapping("/updateMember")
    public ResponseEntity<Map<String, Object>> updateMember(@RequestParam Long id,
                                                            @RequestParam String name,
                                                            @RequestPart(required = false) MultipartFile photo) throws IOException {
        log.info("사용자 정보 수정 요청: memberId={}, name={}", id, name);
        Member existingMember = memberService.findMember(id);
        if (existingMember == null) {
            log.error("사용자를 찾을 수 없습니다: memberId={}", id);
            throw new IllegalStateException("사용자를 찾을 수 없습니다.");
        }

        String photoFilename = existingMember.getPhotoPath();
        if (photo != null && !photo.isEmpty()) {
            photoFilename = saveMultipartFile(photo, id, "photo");
        }

        Member updatedMember = memberService.updateMember(id, name, photoFilename, existingMember.getVideoPath2());
        log.info("사용자 정보 수정 성공: {}", updatedMember);
        return createResponse("사용자 수정 성공!", updatedMember);
    }

    @DeleteMapping("/deleteMember")
    public ResponseEntity<Map<String, Object>> deleteMember(@RequestParam Long memberId) {
        log.info("사용자 삭제 요청: memberId={}", memberId);
        memberService.deleteMember(memberId);
        log.info("사용자 삭제 성공: memberId={}", memberId);
        return createResponse("사용자 삭제 성공!", null);
    }

    @GetMapping("/findMember")
    public ResponseEntity<Resource> findMember(@RequestParam Long memberId) throws IOException {
        log.info("사용자 조회 요청: memberId={}", memberId);
        Member member = memberService.findMember(memberId);
        if (member == null) {
            log.error("사용자를 찾을 수 없습니다: memberId={}", memberId);
            return ResponseEntity.notFound().build();
        }

        File photoFile = new File(UPLOAD_DIR + "/photo/" + member.getPhotoPath());
        if (!photoFile.exists()) {
            log.error("사진 파일을 찾을 수 없습니다: {}", member.getPhotoPath());
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(photoFile);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + photoFile.getName() + "\"")
                .body(resource);
    }

    @GetMapping("/findAllMembers")
    public ResponseEntity<MultiValueMap<String, Object>> findAllMembers(@RequestParam Long homeId) throws IOException {
        log.info("전체 사용자 조회 요청: homeId={}", homeId);
        List<Member> members = memberService.findAllMembers(homeId);

        if (members.isEmpty()) {
            log.info("사용자가 아직 등록되지 않았습니다.");
        }

        // MultiValueMap을 사용하여 같은 키에 대해 JSON 데이터와 파일 데이터를 함께 전송
        MultiValueMap<String, Object> responseData = new LinkedMultiValueMap<>();

        // 조회된 각 사용자를 JSON 데이터 및 사진 파일을 응답 데이터에 추가
        for (Member member : members) {
            log.info("memberId={}", member.getId());

            // Content-Type: application-Type
            HttpHeaders jsonHeaders = new HttpHeaders();
            jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> memberData = new HashMap<>();
            memberData.put("memberId", member.getId());
            memberData.put("name", member.getName());

            responseData.add("memberData", new HttpEntity<>(memberData, jsonHeaders));

            // 사용자의 사진 파일을 응답 데이터에 추가
            String photoFilename = member.getPhotoPath();
            if (photoFilename != null && !photoFilename.isEmpty()) {
                File photoFile = new File(UPLOAD_DIR + "/photo/" + photoFilename);

                if (photoFile.exists()) {
                    Resource resource = new FileSystemResource(photoFile);

                    HttpHeaders fileHeaders = new HttpHeaders();
                    fileHeaders.setContentDispositionFormData("photo", photoFile.getName());
                    fileHeaders.setContentType(MediaType.IMAGE_PNG);

                    responseData.add("memberData", new HttpEntity<>(resource, fileHeaders));
                    log.info("filename={}", photoFilename);
                }
            }
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        log.info("전체 사용자 조회 성공, homeId={}", homeId);

        return new ResponseEntity<>(responseData, headers, HttpStatus.OK);
    }

    // 조각 업로드 처리 로직
    private void handleChunkedUpload(Long id, String chunk, Integer chunkIndex, Integer totalChunks) {
        if (chunk != null && chunkIndex != null && totalChunks != null) {
            uploadSessions.computeIfAbsent(id, k -> new StringBuilder()).append(chunk);
            uploadedChunks.merge(id, 1, Integer::sum);
            log.info("memberId={}, chunkIndex={}/{}", id, chunkIndex + 1, totalChunks);
        } else {
            log.error("조각 정보가 잘못되었습니다: memberId={}", id);
            throw new IllegalStateException("조각 정보가 잘못되었습니다.");
        }
    }

    // Base64 인코딩된 파일 저장 및 파일명 반환
    private String saveBase64EncodedFile(String base64Data, Long id, String fileType) throws IOException {
        byte[] decodedBytes = Base64.getDecoder().decode(base64Data);
        String fileExtension = getFileExtension(fileType);
        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String baseFileName = date + "_member" + id;
        File dest = createUniqueFile(getDeviceUploadDir(fileType), baseFileName, fileExtension);

        try (FileOutputStream fos = new FileOutputStream(dest)) {
            fos.write(decodedBytes);
        }

        return dest.getName();
    }

    // 파일 확장자 반환
    private String getFileExtension(String fileType) {
        switch (fileType.toLowerCase()) {
            case "video":
            case "video2":
                return ".mp4";
            case "photo":
                return ".png";
            default:
                throw new IllegalArgumentException("지원하지 않는 파일 유형: " + fileType);
        }
    }

    // 디렉토리 생성 및 고유한 파일명 반환
    private File createUniqueFile(String deviceUploadDir, String baseFileName, String fileExtension) throws IOException {
        File dir = new File(deviceUploadDir);
        if (!dir.exists() && !dir.mkdirs()) {
            log.error("디렉토리 생성 실패: {}", deviceUploadDir);
            throw new IOException("디렉토리 생성에 실패했습니다: " + deviceUploadDir);
        }

        int counter = 1;
        File dest;
        do {
            String uniqueFileName = counter == 1 ? baseFileName + fileExtension
                    : baseFileName + "_" + counter + fileExtension;
            dest = new File(deviceUploadDir, uniqueFileName);
            counter++;
        } while (dest.exists());

        return dest;
    }

    // MultipartFile 저장 및 파일명 반환
    private String saveMultipartFile(MultipartFile file, Long id, String fileType) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String baseFileName = date + "_member" + id;
        File dest = createUniqueFile(getDeviceUploadDir(fileType), baseFileName, fileExtension);

        try (FileOutputStream fos = new FileOutputStream(dest)) {
            fos.write(file.getBytes());
        }

        return dest.getName();
    }

    // 디렉토리 경로 반환
    private String getDeviceUploadDir(String fileType) {
        return UPLOAD_DIR + "/" + (fileType.equals("photo") ? "photo" : "video");
    }

    private ResponseEntity<Map<String, Object>> createResponse(String message, Object data, Long homeId) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "200");
        response.put("message", message);
        response.put("data", data);
        response.put("homeId", homeId);
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> createResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "200");
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
}
