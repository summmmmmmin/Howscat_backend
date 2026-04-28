package com.example.howscat.controller;

import com.example.howscat.UserService;
import com.example.howscat.dto.LoginRequest;
import com.example.howscat.dto.LoginResponse;
import com.example.howscat.dto.RefreshRequest;
import com.example.howscat.dto.SignupRequest;
import com.example.howscat.jwt.JwtProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtProvider jwtProvider;
    private final RedisTemplate<String, String> redisTemplate;

    // 회원가입
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody @Valid SignupRequest request) {

        userService.signup(request);

        return ResponseEntity.ok(
                Map.of("success", true, "message", "회원가입 성공")
        );
    }

    // 로그인
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequest request) {

        LoginResponse response = userService.login(request);

        return ResponseEntity.ok(response);
    }

    // 🔥 Refresh 재발급 (Rotation 적용)
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {

        String refreshToken = request.getRefreshToken();

        // 1️⃣ 유효성 검사
        if (!jwtProvider.validateToken(refreshToken)) {
            return ResponseEntity.status(401)
                    .body(Map.of("message", "유효하지 않은 Refresh Token"));
        }

        Integer userId = jwtProvider.getUserId(refreshToken);

        // 2️⃣ Redis에 저장된 값 조회
        String savedRefresh =
                redisTemplate.opsForValue().get("refresh:" + userId);

        if (savedRefresh == null || !savedRefresh.equals(refreshToken)) {
            return ResponseEntity.status(401)
                    .body(Map.of("message", "Refresh Token 불일치"));
        }

        // 3️⃣ 새 토큰 발급 (Rotation)
        String newAccess = jwtProvider.createAccessToken(userId);
        String newRefresh = jwtProvider.createRefreshToken(userId);

        redisTemplate.opsForValue()
                .set("refresh:" + userId,
                        newRefresh,
                        Duration.ofDays(14));

        return ResponseEntity.ok(
                Map.of(
                        "accessToken", newAccess,
                        "refreshToken", newRefresh
                )
        );
    }

    // 로그아웃
    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Authorization 헤더가 없거나 형식이 올바르지 않습니다."));
        }

        String token = authHeader.substring(7);

        try {
            if (jwtProvider.validateToken(token)) {
                long remaining = jwtProvider.getRemainingExpiration(token);
                Integer userId = jwtProvider.getUserId(token);

                if (remaining > 0) {
                    redisTemplate.opsForValue()
                            .set("blacklist:" + token, "logout", Duration.ofMillis(remaining));
                }
                redisTemplate.delete("refresh:" + userId);
            } else {
                // 만료된 토큰: refresh만 삭제
                try {
                    Integer userId = jwtProvider.getUserIdIgnoreExpiration(token);
                    redisTemplate.delete("refresh:" + userId);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.warn("로그아웃 처리 중 오류 (무시): {}", e.getMessage());
        }

        return ResponseEntity.ok(Map.of("message", "로그아웃 완료"));
    }
}
