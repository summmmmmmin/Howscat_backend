package com.example.howscat.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // 로그인·회원가입: 분당 10회
    private static final int AUTH_MAX = 10;
    private static final long AUTH_WINDOW_MS = 60_000;

    // AI 분석: 분당 5회
    private static final int AI_MAX = 5;
    private static final long AI_WINDOW_MS = 60_000;

    private final Map<String, Deque<Long>> authBucket = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> aiBucket = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String ip = getClientIp(request);

        if (path.equals("/api/users/login") || path.equals("/api/users/signup")) {
            if (isLimited(ip, authBucket, AUTH_MAX, AUTH_WINDOW_MS)) {
                reject(response);
                return;
            }
        } else if (path.contains("/vomit") || path.contains("/ai-summary")) {
            if (isLimited(ip, aiBucket, AI_MAX, AI_WINDOW_MS)) {
                reject(response);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isLimited(String ip, Map<String, Deque<Long>> bucket, int max, long windowMs) {
        long now = System.currentTimeMillis();
        bucket.compute(ip, (k, deque) -> {
            if (deque == null) deque = new ArrayDeque<>();
            while (!deque.isEmpty() && now - deque.peekFirst() > windowMs) {
                deque.pollFirst();
            }
            deque.addLast(now);
            return deque;
        });
        Deque<Long> deque = bucket.get(ip);
        return deque != null && deque.size() > max;
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\"}");
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
