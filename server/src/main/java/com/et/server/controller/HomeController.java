package com.et.server.controller;

import com.et.server.Home;
import com.et.server.service.HomeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/et/home")
public class HomeController {

    @Autowired
    private HomeService homeService;

    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> signup(@RequestBody Home home) {
        log.info("회원가입 요청: name={}", home.getName());
        Home newHome = homeService.signup(home);
        log.info("회원가입 성공: homeId={}", newHome.getId());
        return createResponse("회원가입 성공!", newHome);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestParam String name, @RequestParam String password) {
        log.info("로그인 요청: name={}", name);
        Long homeId = homeService.login(name, password);
        if (homeId != null) {
            log.info("로그인 성공: homeId={}", homeId);
            return createResponse("로그인 성공!", homeId);
        } else {
            log.warn("로그인 실패: name={}", name);
            return createResponse("로그인 실패!", null);
        }
    }

    private ResponseEntity<Map<String, Object>> createResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "200");
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
}
