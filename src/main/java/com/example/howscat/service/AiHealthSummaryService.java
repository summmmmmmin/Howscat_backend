package com.example.howscat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiHealthSummaryService {

    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;
    private final CatOwnershipService catOwnershipService;

    @Value("${GEMINI_API_KEY:}")
    private String apiKey;

    public String getSummary(Long catId, Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        catOwnershipService.assertOwner(catId, userId);

        if (apiKey == null || apiKey.isBlank()) {
            return "🔑 AI 분석 키가 설정되지 않았어요.";
        }

        String cached = getCachedSummary(catId);
        if (cached != null) return cached;

        String catName = getCatName(catId);
        HealthData data = collectHealthData(catId);

        if (!data.hasAnyData()) {
            return "🐾 아직 건강 기록이 없어요. 케어 탭에서 기록을 시작해보세요!";
        }

        String prompt = buildPrompt(catName, data);
        String result = callGemini(prompt);
        if (!result.contains("불러올 수 없어요")) cacheSummary(catId, result);
        return result;
    }

    private HealthData collectHealthData(Long catId) {
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAgo = today.minusDays(7);
        HealthData data = new HealthData();

        // 최근 7일 구토 횟수
        try {
            data.vomitCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vomit_record WHERE cat_id = ? AND created_at >= ?",
                    Integer.class, catId, sevenDaysAgo);
        } catch (Exception e) {
            log.debug("vomit count 조회 실패: {}", e.getMessage());
        }

        // 구토 주요 색상
        if (data.vomitCount != null && data.vomitCount > 0) {
            try {
                data.topVomitColor = jdbcTemplate.queryForObject(
                        "SELECT vs.color FROM vomit_record vr " +
                        "JOIN vomit_status vs ON vr.vomit_status_id = vs.vomit_status_id " +
                        "WHERE vr.cat_id = ? AND vr.created_at >= ? " +
                        "GROUP BY vs.color ORDER BY COUNT(*) DESC LIMIT 1",
                        String.class, catId, sevenDaysAgo);
            } catch (Exception e) {
                log.debug("vomit color 조회 실패: {}", e.getMessage());
            }

            // 최근 구토 상세 (ai_result + 위험도)
            try {
                data.recentVomitDetails = jdbcTemplate.queryForList(
                        "SELECT vr.ai_result, vs.severity_level " +
                        "FROM vomit_record vr " +
                        "LEFT JOIN vomit_status vs ON vr.vomit_status_id = vs.vomit_status_id " +
                        "WHERE vr.cat_id = ? AND vr.created_at >= ? " +
                        "ORDER BY vr.created_at DESC LIMIT 3",
                        new Object[]{catId, sevenDaysAgo});
            } catch (Exception e) {
                log.debug("vomit detail 조회 실패: {}", e.getMessage());
            }
        }

        // 최근 2개 체중 기록
        try {
            List<Double> weights = jdbcTemplate.queryForList(
                    "SELECT weight FROM weight_record WHERE cat_id = ? ORDER BY recorded_at DESC LIMIT 2",
                    Double.class, catId);
            if (!weights.isEmpty()) data.latestWeight = weights.get(0);
            if (weights.size() > 1) data.prevWeight = weights.get(1);
        } catch (Exception e) {
            log.debug("weight 조회 실패: {}", e.getMessage());
        }

        // 최근 비만도
        try {
            data.obesityLevel = jdbcTemplate.query(
                    "SELECT obesity_level FROM obesity_check_record WHERE cat_id = ? ORDER BY checked_at DESC LIMIT 1",
                    rs -> rs.next() ? rs.getString("obesity_level") : null,
                    catId);
        } catch (Exception e) {
            log.debug("obesity 조회 실패: {}", e.getMessage());
        }

        // 30일 내 예정 건강 일정
        try {
            data.upcomingScheduleCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM health_schedule WHERE cat_id = ? AND next_date BETWEEN ? AND ?",
                    Integer.class, catId, today, today.plusDays(30));
        } catch (Exception e) {
            log.debug("schedule 조회 실패: {}", e.getMessage());
        }

        return data;
    }

    private String buildPrompt(String catName, HealthData d) {
        StringBuilder sb = new StringBuilder();
        sb.append("고양이 '").append(catName).append("'의 최근 건강 데이터:\n");

        sb.append("- 최근 7일 구토: ")
          .append(d.vomitCount != null ? d.vomitCount + "회" : "기록없음");
        if (d.topVomitColor != null) sb.append(" (주요 색: ").append(d.topVomitColor).append(")");
        sb.append("\n");

        if (d.recentVomitDetails != null && !d.recentVomitDetails.isEmpty()) {
            sb.append("- 최근 구토 상세:\n");
            for (Map<String, Object> row : d.recentVomitDetails) {
                String aiResult = (String) row.get("ai_result");
                String severity = (String) row.get("severity_level");
                if (aiResult != null && !aiResult.isBlank()) {
                    sb.append("  · ").append(aiResult);
                    if (severity != null) sb.append(" (위험도: ").append(severity).append(")");
                    sb.append("\n");
                }
            }
        }

        if (d.latestWeight != null) {
            sb.append("- 체중: ").append(d.latestWeight).append("kg");
            if (d.prevWeight != null) {
                double diff = d.latestWeight - d.prevWeight;
                sb.append(String.format(" (이전 대비 %+.2fkg)", diff));
            }
            sb.append("\n");
        }

        if (d.obesityLevel != null) {
            sb.append("- 비만도: ").append(translateObesityLevel(d.obesityLevel)).append("\n");
        }

        if (d.upcomingScheduleCount != null && d.upcomingScheduleCount > 0) {
            sb.append("- 30일 내 건강 일정: ").append(d.upcomingScheduleCount).append("건\n");
        }

        sb.append("\n위 데이터를 보고 집사에게 건강 조언 문장을 하나만 작성해주세요.\n");
        sb.append("반드시 지킬 규칙:\n");
        sb.append("1. 이모지 1개로 시작\n");
        sb.append("2. 완전한 한국어 문장으로 끝맺음\n");
        sb.append("3. 구토가 있으면 색상과 위험도를 언급\n");
        sb.append("4. 데이터 그대로 나열하지 말고 조언으로 재구성\n");
        sb.append("출력 예시: 🔴 붉은 구토가 감지됐어요. 즉시 동물병원 방문을 권장합니다.\n");
        sb.append("출력 예시: 🟡 노란 구토 2회, 공복 시간을 줄이고 소량씩 급여해보세요.\n");
        sb.append("출력 예시: ✅ 이번 주 건강 상태 양호! 체중도 안정적이에요.\n");
        sb.append("조언 문장만 출력하고 다른 말은 하지 마세요.");

        return sb.toString();
    }

    private String callGemini(String prompt) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of("maxOutputTokens", 8192)
        );

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> candidates =
                        (List<Map<String, Object>>) response.getBody().get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        for (Map<String, Object> part : parts) {
                            if (!Boolean.TRUE.equals(part.get("thought"))) {
                                Object text = part.get("text");
                                if (text instanceof String) return (String) text;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Gemini API 호출 실패: {}", e.getMessage());
        }
        return "🐾 현재 AI 분석을 불러올 수 없어요.";
    }

    private String getCachedSummary(Long catId) {
        try {
            return jdbcTemplate.query(
                "SELECT summary FROM ai_health_summary_cache WHERE cat_id = ? AND generated_at >= DATE_SUB(NOW(), INTERVAL 6 HOUR)",
                rs -> rs.next() ? rs.getString("summary") : null,
                catId);
        } catch (Exception e) { return null; }
    }

    private void cacheSummary(Long catId, String summary) {
        try {
            jdbcTemplate.update(
                "INSERT INTO ai_health_summary_cache (cat_id, summary, generated_at) VALUES (?, ?, NOW()) " +
                "ON DUPLICATE KEY UPDATE summary = VALUES(summary), generated_at = NOW()",
                catId, summary);
        } catch (Exception e) { log.warn("AI 요약 캐시 저장 실패: {}", e.getMessage()); }
    }

    private String getCatName(Long catId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT name FROM cat WHERE cat_id = ?", String.class, catId);
        } catch (Exception e) {
            return "고양이";
        }
    }

    private String translateObesityLevel(String level) {
        return switch (level) {
            case "UNDERWEIGHT" -> "저체중";
            case "NORMAL" -> "정상";
            case "SLIGHTLY_OVERWEIGHT" -> "약간 과체중";
            case "OVERWEIGHT" -> "과체중";
            default -> level;
        };
    }

    private static class HealthData {
        Integer vomitCount;
        String topVomitColor;
        List<Map<String, Object>> recentVomitDetails;
        Double latestWeight;
        Double prevWeight;
        String obesityLevel;
        Integer upcomingScheduleCount;

        boolean hasAnyData() {
            return (vomitCount != null && vomitCount > 0)
                    || latestWeight != null
                    || obesityLevel != null;
        }
    }
}
