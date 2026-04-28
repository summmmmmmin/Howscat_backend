package com.example.howscat.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor   // 🔥 이거 중요
public class JwtFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.length() > 7 && header.startsWith("Bearer ")) {

            String token = header.substring(7);

            // 토큰 길이 제한 (DoS 방지)
            if (token.length() > 2048) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "유효하지 않은 토큰");
                return;
            }

            if (!jwtProvider.validateToken(token)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "유효하지 않은 토큰");
                return;
            }

            // 블랙리스트 확인
            if (redisTemplate.opsForValue().get("blacklist:" + token) != null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "로그아웃된 토큰");
                return;
            }

            Integer userId = jwtProvider.getUserId(token);

            List<GrantedAuthority> authorities =
                    List.of(new SimpleGrantedAuthority("ROLE_USER"));

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            authorities);

            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
