package com.example.howscat;

import com.example.howscat.domain.User;
import com.example.howscat.dto.LoginRequest;
import com.example.howscat.dto.LoginResponse;
import com.example.howscat.dto.SignupRequest;
import com.example.howscat.jwt.JwtProvider;
import com.example.howscat.repository.CatRepository;
import com.example.howscat.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock CatRepository catRepository;
    @Mock JwtProvider jwtProvider;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks UserService userService;

    private SignupRequest makeSignupRequest(String loginId, String password, String name) {
        SignupRequest req = new SignupRequest();
        ReflectionTestUtils.setField(req, "loginId", loginId);
        ReflectionTestUtils.setField(req, "password", password);
        ReflectionTestUtils.setField(req, "name", name);
        return req;
    }

    private LoginRequest makeLoginRequest(String loginId, String password) {
        LoginRequest req = new LoginRequest();
        ReflectionTestUtils.setField(req, "loginId", loginId);
        ReflectionTestUtils.setField(req, "password", password);
        return req;
    }

    @Test
    void 회원가입_정상동작() {
        SignupRequest req = makeSignupRequest("testuser", "password123", "테스트");
        when(userRepository.existsByLoginId("testuser")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");

        userService.signup(req);

        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void 이미_있는_아이디로_가입하면_예외() {
        SignupRequest req = makeSignupRequest("duplicate", "password123", "테스트");
        when(userRepository.existsByLoginId("duplicate")).thenReturn(true);

        assertThatThrownBy(() -> userService.signup(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 존재하는 아이디입니다.");

        verify(userRepository, never()).save(any());
    }

    @Test
    void 로그인_성공하면_토큰_반환() {
        User user = new User("testuser", "encodedPassword", "테스트");
        ReflectionTestUtils.setField(user, "userId", 1);

        when(userRepository.findByLoginId("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encodedPassword")).thenReturn(true);
        when(jwtProvider.createAccessToken(1)).thenReturn("accessToken");
        when(jwtProvider.createRefreshToken(1)).thenReturn("refreshToken");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        LoginRequest req = makeLoginRequest("testuser", "password123");
        LoginResponse response = userService.login(req);

        assertEquals("accessToken", response.getAccessToken());
        assertEquals("refreshToken", response.getRefreshToken());
        // Redis에 refresh 토큰 저장되는지 확인
        verify(valueOps).set(eq("refresh:1"), eq("refreshToken"), any());
    }

    @Test
    void 없는_아이디로_로그인하면_예외() {
        when(userRepository.findByLoginId("nobody")).thenReturn(Optional.empty());
        LoginRequest req = makeLoginRequest("nobody", "password123");

        assertThatThrownBy(() -> userService.login(req))
                .isInstanceOf(AuthException.class)
                .hasMessage("아이디 또는 비밀번호를 확인해주세요.");
    }

    @Test
    void 비밀번호_틀리면_예외() {
        User user = new User("testuser", "encodedPassword", "테스트");
        when(userRepository.findByLoginId("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

        LoginRequest req = makeLoginRequest("testuser", "wrongPassword");

        assertThatThrownBy(() -> userService.login(req))
                .isInstanceOf(AuthException.class)
                .hasMessage("아이디 또는 비밀번호를 확인해주세요.");
    }
}
