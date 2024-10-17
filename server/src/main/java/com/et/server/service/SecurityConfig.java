package com.et.server.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http     // HTTP 요청에 대한 보안 설정
                .csrf(AbstractHttpConfigurer::disable) // CSRF 비활성화
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().permitAll() // 모든 요청을 허용
                )
                .httpBasic(withDefaults()); // HTTP Basic 인증 사용
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {  // 비밀번호를 해시화
        return new BCryptPasswordEncoder();
    }
}
