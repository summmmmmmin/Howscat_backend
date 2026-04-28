package com.example.howscat;

import com.example.howscat.domain.Cat;
import com.example.howscat.dto.LoginRequest;
import com.example.howscat.dto.LoginResponse;
import com.example.howscat.dto.SignupRequest;
import com.example.howscat.repository.CatRepository;
import com.example.howscat.repository.UserRepository;
import com.example.howscat.domain.User;
import com.example.howscat.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CatRepository catRepository;
    private final JwtProvider jwtProvider;
    private final RedisTemplate<String, String> redisTemplate;
    // 회원가입
    public void signup(SignupRequest request) {

        if (userRepository.existsByLoginId(request.getLoginId())) {
            throw new IllegalArgumentException("이미 존재하는 아이디입니다.");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User user = new User(
                request.getLoginId(),
                encodedPassword,
                request.getName()
        );

        userRepository.save(user);
    }

    // 로그인
    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new AuthException("아이디 또는 비밀번호를 확인해주세요."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AuthException("아이디 또는 비밀번호를 확인해주세요.");
        }

        Long lastViewedCatId = user.getLastViewedCatId();
        String lastViewedCatName = null;

        if (lastViewedCatId != null) {
            Cat cat = catRepository.findById(lastViewedCatId)
                    .orElse(null);

            if (cat != null) {
                lastViewedCatName = cat.getName();
            }
        }

        String accessToken = jwtProvider.createAccessToken(user.getUserId());
        String refreshToken = jwtProvider.createRefreshToken(user.getUserId());

        redisTemplate.opsForValue()
                .set("refresh:" + user.getUserId(),
                        refreshToken,
                        Duration.ofDays(14));

        return new LoginResponse(
                user.getLoginId(),
                user.getName(),
                lastViewedCatId,
                lastViewedCatName,
                accessToken,
                refreshToken
        );
    }
}