package com.example.howscat.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class JwtProviderTest {

    private JwtProvider jwtProvider;
    private static final String SECRET = "test-secret-key-must-be-at-least-32bytes!!";

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider();
        ReflectionTestUtils.setField(jwtProvider, "secret", SECRET);
        jwtProvider.validateSecret();
    }

    @Test
    void AccessToken_생성하고_검증되는지_확인() {
        String token = jwtProvider.createAccessToken(1);
        assertTrue(jwtProvider.validateToken(token));
    }

    @Test
    void RefreshToken_생성하고_검증되는지_확인() {
        String token = jwtProvider.createRefreshToken(1);
        assertTrue(jwtProvider.validateToken(token));
    }

    @Test
    void 토큰에서_userId_꺼내기() {
        String token = jwtProvider.createAccessToken(42);
        assertEquals(42, jwtProvider.getUserId(token));
    }

    @Test
    void 이상한_토큰은_검증_실패() {
        assertFalse(jwtProvider.validateToken("invalid.token.value"));
    }

    @Test
    void 빈문자열_토큰은_검증_실패() {
        assertFalse(jwtProvider.validateToken(""));
    }

    @Test
    void secret이_짧으면_예외나야함() {
        JwtProvider provider = new JwtProvider();
        ReflectionTestUtils.setField(provider, "secret", "tooshort");

        assertThatThrownBy(provider::validateSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }

    @Test
    void 만료시간_남아있는지_확인() {
        String token = jwtProvider.createAccessToken(1);
        long remaining = jwtProvider.getRemainingExpiration(token);
        assertTrue(remaining > 0);
    }
}
