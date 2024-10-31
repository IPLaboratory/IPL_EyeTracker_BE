package com.et.server.service;

import com.et.server.entity.Home;
import com.et.server.entity.Member;
import com.et.server.repository.HomeRepository;
import com.et.server.repository.MemberRepository;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Transactional(readOnly=true)
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final HomeRepository homeRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${app.ml-server-url}")
    private String ML_SERVER_URL;

    // 사용자 추가
    @Transactional
    public Member addMember(Long homeId, Member member) {
        Home home = homeRepository.findById(homeId);
        if (home == null) {
            throw new IllegalStateException("홈을 찾을 수 없습니다.");
        }
        member.setHome(home);
        memberRepository.save(member);
        return member;
    }

    // ML 통신: addMember
    public String sendVideoToMLUpload(String name, String videoChunk, Integer chunkIndex, Integer totalChunks) {
        return sendVideoToML(name, videoChunk, chunkIndex, totalChunks, "/upload-video/");
    }

    // ML 통신: addVideo
    public String sendVideoToMLAdd(String name, String videoChunk, Integer chunkIndex, Integer totalChunks) {
        return sendVideoToML(name, videoChunk, chunkIndex, totalChunks, "/add-video/");
    }

    // ML 통신: 사용자 이름, 영상 전송
    private String sendVideoToML(String name, String videoChunk, Integer chunkIndex, Integer totalChunks, String endpoint) {
        // 요청 바디 구성
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("Name", name);
        requestBody.put("videoChunk", videoChunk);
        requestBody.put("chunkIndex", chunkIndex);
        requestBody.put("totalChunks", totalChunks);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            // ML 서버로 POST 요청 전송
            String encodedUrl = UriComponentsBuilder.fromHttpUrl(ML_SERVER_URL + endpoint)
                    .encode()
                    .toUriString();
            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    encodedUrl,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            // 응답 처리
            log.info("ML 서버 응답 상태 코드: {}", responseEntity.getStatusCode());
            return responseEntity.getBody();
        } catch (Exception e) {
            log.error("ML 서버와의 통신 중 오류 발생", e);
            return null;
        }
    }

    // 사용자 수정
    @Transactional
    public Member updateMember(Long memberId, String name, String photoPath, String videoPath2) {
        Member member = memberRepository.findById(memberId);
        if (member == null) {
            throw new IllegalStateException("사용자를 찾을 수 없습니다.");
        }
        member.setName(name);

        if (photoPath != null) {
            member.setPhotoPath(photoPath);
        }
        if (videoPath2 != null) {
            member.setVideoPath2(videoPath2);
        }
        memberRepository.save(member);
        return member;
    }

    // 사용자 삭제
    @Transactional
    public void deleteMember(Long memberId) {
        Member member = memberRepository.findById(memberId);
        if (member == null) {
            throw new IllegalStateException("존재하지 않는 사용자입니다.");
        }
        memberRepository.delete(member);
    }

    // 사용자 조회
    public Member findMember(Long memberId) {
        Member member = memberRepository.findById(memberId);
        if (member == null) {
            throw new IllegalStateException("사용자를 찾을 수 없습니다.");
        }
        return member;
    }

    // 전체 사용자 조회
    public List<Member> findAllMembers(Long homeId) {
        return memberRepository.findAll(homeId);
    }
}
