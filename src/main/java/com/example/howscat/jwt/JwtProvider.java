package com.example.howscat.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtProvider {

    @Value("${jwt.secret}")
    private String secret;

    private final long ACCESS_EXP = 1000 * 60 * 30;   // 30분
    private final long REFRESH_EXP = 1000 * 60 * 60 * 24 * 14; // 14일

    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.getBytes().length < 32) {
            throw new IllegalStateException(
                "JWT_SECRET은 32바이트(256비트) 이상이어야 합니다. 현재 길이: "
                + (secret == null ? 0 : secret.getBytes().length) + "바이트"
            );
        }
    }

    private Key getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    // Access Token
    public String createAccessToken(Integer userId) {
        return createToken(userId, ACCESS_EXP);
    }

    // Refresh Token
    public String createRefreshToken(Integer userId) {
        return createToken(userId, REFRESH_EXP);
    }

    private String createToken(Integer userId, long exp) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + exp))
                .signWith(getKey())
                .compact();
    }

    public Integer getUserId(String token) {
        return Integer.parseInt(
                Jwts.parserBuilder()
                        .setSigningKey(getKey())
                        .build()
                        .parseClaimsJws(token)
                        .getBody()
                        .getSubject()
        );
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getRemainingExpiration(String token) {
        Date expiration = Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getExpiration();

        return expiration.getTime() - System.currentTimeMillis();
    }

}
