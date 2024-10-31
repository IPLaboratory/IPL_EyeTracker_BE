package com.et.server.service;

import com.et.server.entity.Home;
import com.et.server.repository.HomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class HomeService {

    private final HomeRepository homeRepository;
    private final PasswordEncoder passwordEncoder;

    // 회원 가입
    @Transactional
    public Home signup(Home home) {
        validateDuplicate(home);
        // 비밀번호 암호화
        home.setPassword(passwordEncoder.encode(home.getPassword()));
        homeRepository.save(home);
        return home;
    }

    // 중복 회원 검증
    private void validateDuplicate(Home home) {
        List<Home> findhomes = homeRepository.findByName(home.getName());
        if (!findhomes.isEmpty()) {
            throw new IllegalStateException("이미 존재하는 회원입니다.");
        }
    }

    // 로그인
    public Long login(String name, String password) {
        List<Home> homes = homeRepository.findByName(name);
        if (homes.isEmpty()) {
            throw new IllegalStateException("로그인 정보가 올바르지 않습니다.");
        }
        Home home = homes.get(0);
        if (!passwordEncoder.matches(password, home.getPassword())) {
            throw new IllegalStateException("로그인 정보가 올바르지 않습니다.");
        }
        return home.getId();
    }
}
