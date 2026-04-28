package com.example.howscat.service;

import com.example.howscat.dto.VomitAnalysisRequest;
import com.example.howscat.dto.VomitAnalysisResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VomitAnalysisService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${GEMINI_API_KEY:}")
    private String geminiApiKey;

    public VomitAnalysisResponse analyzeAndSave(Long catId,
                                                  VomitAnalysisRequest request,
                                                  Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        assertCatBelongsToUser(catId, userId);

        // 1) Gemini Vision으로 이미지 분석
        GeminiVisionResult vision = analyzeImageWithGemini(request.getImageBase64());

        String color = vision.color;
        String shape = toShape(vision.hasFoam, vision.hasFood, vision.hasForeign, vision.foreignObjectType);

        // 2) vomit_status 조회
        VomitStatus status = findVomitStatus(color, shape);

        // 3) ai_result 문자열 구성 (색상 + 이물질 종류)
        String aiResult = buildAiResult(color, vision.hasFoam, vision.hasFood, vision.hasForeign, vision.foreignObjectType);

        // 4) vomit_record 저장
        insertVomitRecord(
                catId,
                status.vomitStatusId,
                request.getMemo(),
                aiResult,
                false,
                request.getImagePath()
        );

        // 5) AI 건강 요약 캐시 무효화 (구토 기록 추가 시 최신 데이터 반영)
        try {
            jdbcTemplate.update("DELETE FROM ai_health_summary_cache WHERE cat_id = ?", catId);
        } catch (Exception e) {
            log.warn("AI 요약 캐시 무효화 실패: {}", e.getMessage());
        }
        String riskLevel = normalizeRiskLevel(status.severityLevel);
        boolean urgent = "HIGH".equals(riskLevel);

        return new VomitAnalysisResponse(
                status.vomitStatusId,
                status.severityLevel,
                status.guideText,
                riskLevel,
                urgent,
                vision.aiGuide,
                aiResult
        );
    }

    public void deleteVomitRecord(Long catId, Long vomitId, Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        assertCatBelongsToUser(catId, userId);

        Integer exists = jdbcTemplate.query(
                "SELECT 1 FROM vomit_record WHERE vomit_record_id = ? AND cat_id = ? LIMIT 1",
                new Object[]{vomitId, catId},
                rs -> rs.next() ? 1 : null
        );
        if (exists == null) {
            throw new IllegalArgumentException("vomit record not found");
        }

        jdbcTemplate.update("DELETE FROM vomit_record WHERE vomit_record_id = ? AND cat_id = ?", vomitId, catId);
    }

    private void assertCatBelongsToUser(Long catId, Integer userId) {
        Long ownerUserId = jdbcTemplate.query(
                "SELECT user_id FROM cat WHERE cat_id = ?",
                new Object[]{catId},
                rs -> rs.next() ? rs.getLong("user_id") : null
        );
        if (ownerUserId == null) throw new IllegalArgumentException("cat not found");
        if (ownerUserId.longValue() != userId.longValue()) throw new SecurityException("cat does not belong to user");
    }

    private GeminiVisionResult analyzeImageWithGemini(String imageBase64) {
        GeminiVisionResult fallback = new GeminiVisionResult("UNKNOWN", false, false, false,
                null, "사진 분석을 완료했어요. 수의사 상담이 필요하면 병원 탭을 이용해보세요.");

        if (imageBase64 == null || imageBase64.isBlank() || geminiApiKey.isBlank()) {
            return fallback;
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;
        RestTemplate restTemplate = new RestTemplate();

        String prompt = "이 사진은 고양이가 구토한 것을 찍은 사진입니다. 아래 JSON 형식으로만 답하세요. 다른 설명 없이 JSON만 출력하세요.\n" +
                "color 선택 기준:\n" +
                "- WHITE: 흰색, 투명, 연한 크림색\n" +
                "- YELLOW: 노란색, 황색, 담즙색\n" +
                "- GREEN: 초록색, 녹색\n" +
                "- RED: 붉은색, 혈색, 분홍색\n" +
                "- BROWN: 갈색, 진한 갈색, 음식색\n" +
                "- BLACK: 검은색, 매우 어두운 색\n" +
                "- UNKNOWN: 색상을 전혀 판단할 수 없는 경우에만 사용\n" +
                "색상이 조금이라도 보이면 UNKNOWN 대신 가장 가까운 색을 선택하세요.\n" +
                "{\n" +
                "  \"color\": \"WHITE|YELLOW|GREEN|RED|BROWN|BLACK|UNKNOWN 중 하나\",\n" +
                "  \"hasFoam\": true 또는 false,\n" +
                "  \"hasFood\": true 또는 false,\n" +
                "  \"hasForeign\": true 또는 false,\n" +
                "  \"foreignObjectType\": \"이물질이 없으면 null, 있으면 종류 (예: 헤어볼, 풀, 비닐, 실, 장난감 등 10자 이내)\",\n" +
                "  \"aiGuide\": \"집사에게 한 줄 건강 조언 (30자 이내, 이모지 1개 포함)\"\n" +
                "}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(
                        Map.of("inlineData", Map.of("mimeType", "image/jpeg", "data", imageBase64)),
                        Map.of("text", prompt)
                ))),
                "generationConfig", Map.of(
                        "maxOutputTokens", 8192
                )
        );

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return fallback;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.getBody().get("candidates");
            if (candidates == null || candidates.isEmpty()) return fallback;

            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            if (parts == null || parts.isEmpty()) return fallback;

            String json = null;
            for (Map<String, Object> part : parts) {
                if (!Boolean.TRUE.equals(part.get("thought"))) {
                    Object text = part.get("text");
                    if (text instanceof String) { json = (String) text; break; }
                }
            }
            if (json == null) return fallback;
            log.info("Gemini Vision raw text: {}", json);
            return parseVisionJson(json, fallback);

        } catch (Exception e) {
            log.error("Gemini Vision 호출 실패: {}", e.getMessage());
            return fallback;
        }
    }

    private GeminiVisionResult parseVisionJson(String raw, GeminiVisionResult fallback) {
        try {
            String json = raw.trim();
            // JSON 객체 부분만 추출 ({ ... }) — 백틱/마크다운 코드블록 등 앞뒤 텍스트 제거
            int braceStart = json.indexOf('{');
            int braceEnd = json.lastIndexOf('}');
            if (braceStart < 0 || braceEnd <= braceStart) {
                log.warn("Gemini Vision 응답에 JSON 객체 없음: {}", raw);
                return fallback;
            }
            json = json.substring(braceStart, braceEnd + 1);
            log.info("Gemini Vision extracted JSON: {}", json);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(json);

            String color = node.path("color").asText("UNKNOWN").toUpperCase(Locale.ROOT);
            boolean hasFoam = node.path("hasFoam").asBoolean(false);
            boolean hasFood = node.path("hasFood").asBoolean(false);
            boolean hasForeign = node.path("hasForeign").asBoolean(false);
            String foreignObjectType = node.path("foreignObjectType").isNull() ? null
                    : node.path("foreignObjectType").asText(null);
            String aiGuide = node.path("aiGuide").asText(fallback.aiGuide);

            // color 값이 허용 목록에 없으면 한국어·부분 문자열로 재매핑 시도
            List<String> validColors = List.of("WHITE", "YELLOW", "GREEN", "RED", "BROWN", "BLACK");
            if (!validColors.contains(color)) {
                color = mapColorFuzzy(color);
            }
            log.info("Gemini Vision parsed: color={}, hasFoam={}, hasFood={}, hasForeign={}", color, hasFoam, hasFood, hasForeign);

            return new GeminiVisionResult(color, hasFoam, hasFood, hasForeign, foreignObjectType, aiGuide);
        } catch (Exception e) {
            log.warn("Gemini Vision JSON 파싱 실패: {}", e.getMessage());
            return fallback;
        }
    }

    private static class GeminiVisionResult {
        final String color;
        final boolean hasFoam;
        final boolean hasFood;
        final boolean hasForeign;
        final String foreignObjectType;
        final String aiGuide;

        GeminiVisionResult(String color, boolean hasFoam, boolean hasFood, boolean hasForeign,
                           String foreignObjectType, String aiGuide) {
            this.color = color;
            this.hasFoam = hasFoam;
            this.hasFood = hasFood;
            this.hasForeign = hasForeign;
            this.foreignObjectType = foreignObjectType;
            this.aiGuide = aiGuide;
        }
    }

    private String buildAiResult(String color, boolean hasFoam, boolean hasFood,
                                  boolean hasForeign, String foreignObjectType) {
        StringBuilder sb = new StringBuilder();
        sb.append(colorKorean(color));
        if (hasFoam) sb.append(" · 거품");
        if (hasFood) sb.append(" · 미소화 사료");
        if (hasForeign) {
            sb.append(" · 이물질");
            if (foreignObjectType != null && !foreignObjectType.isBlank()) {
                sb.append("(").append(foreignObjectType).append(")");
            }
        }
        return sb.toString();
    }

    private String mapColorFuzzy(String raw) {
        if (raw == null) return "UNKNOWN";
        String s = raw.toUpperCase(Locale.ROOT);
        if (s.contains("WHITE") || s.contains("흰") || s.contains("투명") || s.contains("크림")) return "WHITE";
        if (s.contains("YELLOW") || s.contains("노란") || s.contains("황") || s.contains("담즙")) return "YELLOW";
        if (s.contains("GREEN") || s.contains("초록") || s.contains("녹")) return "GREEN";
        if (s.contains("RED") || s.contains("붉") || s.contains("혈") || s.contains("분홍")) return "RED";
        if (s.contains("BROWN") || s.contains("갈")) return "BROWN";
        if (s.contains("BLACK") || s.contains("검")) return "BLACK";
        return "UNKNOWN";
    }

    private String colorKorean(String color) {
        return switch (color) {
            case "WHITE" -> "흰색";
            case "YELLOW" -> "노란색";
            case "GREEN" -> "녹색";
            case "RED" -> "붉은색";
            case "BROWN" -> "갈색";
            case "BLACK" -> "검은색";
            default -> "색불명";
        };
    }

    private String toShape(boolean hasFoam, boolean hasFood, boolean hasForeign, String foreignObjectType) {
        // 털/헤어볼은 고양이 정상 반응 — 위험 이물질로 취급하지 않음
        boolean isHair = hasForeign && foreignObjectType != null &&
                (foreignObjectType.contains("털") || foreignObjectType.contains("헤어볼") ||
                 foreignObjectType.contains("모발") || foreignObjectType.toLowerCase(java.util.Locale.ROOT).contains("hair"));
        boolean effectiveForeign = hasForeign && !isHair;

        if (hasFoam && hasFood && effectiveForeign) return "FOAM_FOOD_FOREIGN";
        if (hasFoam && hasFood) return "FOAM_FOOD";
        if (hasFoam && effectiveForeign) return "FOAM_FOREIGN";
        if (hasFood && effectiveForeign) return "FOOD_FOREIGN";
        if (hasFoam) return "FOAM";
        if (hasFood) return "FOOD";
        if (effectiveForeign) return "FOREIGN";
        return "NORMAL";
    }

    private String normalizeRiskLevel(String severityLevel) {
        if (severityLevel == null || severityLevel.isBlank()) return "MEDIUM";
        String s = severityLevel.trim().toUpperCase(Locale.ROOT);
        if (s.contains("HIGH") || s.contains("DANGER") || s.contains("위험")) return "HIGH";
        if (s.contains("LOW") || s.contains("SAFE") || s.contains("안전")) return "LOW";
        return "MEDIUM";
    }

    private VomitStatus findVomitStatus(String color, String shape) {
        // 색+형태 정확 매칭
        VomitStatus status = queryStatus(color, shape);
        if (status != null) return status;

        // 같은 색에서 NORMAL fallback
        status = queryStatus(color, "NORMAL");
        if (status != null) return status;

        // UNKNOWN 색 fallback
        status = queryStatus("UNKNOWN", shape);
        if (status != null) return status;

        status = queryStatus("UNKNOWN", "NORMAL");
        if (status != null) return status;

        // 마지막 보정: 색 무관(shape 기준)으로 찾아서라도 기능이 동작하게 함
        status = queryStatusByShape(shape);
        if (status != null) return status;

        status = queryStatusByShape("NORMAL");
        if (status != null) return status;

        throw new IllegalArgumentException("vomit_status not found for color=" + color + ", shape=" + shape);
    }

    private VomitStatus queryStatus(String color, String shape) {
        String sql = "SELECT vomit_status_id, severity_level, guide_text FROM vomit_status WHERE color = ? AND shape = ? LIMIT 1";
        return jdbcTemplate.query(sql, new Object[]{color, shape}, rs -> {
            if (!rs.next()) return null;
            return new VomitStatus(
                    rs.getLong("vomit_status_id"),
                    rs.getString("severity_level"),
                    rs.getString("guide_text")
            );
        });
    }

    private VomitStatus queryStatusByShape(String shape) {
        String sql = "SELECT vomit_status_id, severity_level, guide_text FROM vomit_status WHERE shape = ? LIMIT 1";
        return jdbcTemplate.query(sql, new Object[]{shape}, rs -> {
            if (!rs.next()) return null;
            return new VomitStatus(
                    rs.getLong("vomit_status_id"),
                    rs.getString("severity_level"),
                    rs.getString("guide_text")
            );
        });
    }

    private Long insertVomitRecord(Long catId,
                                    Long vomitStatusId,
                                    String memo,
                                    String aiResult,
                                    boolean hasHair,
                                    String imagePath) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO vomit_record (cat_id, vomit_status_id, memo, ai_result, created_at, has_hair, image_path) " +
                            "VALUES (?, ?, ?, ?, NOW(), ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, catId);
            ps.setLong(2, vomitStatusId);
            ps.setString(3, memo);
            ps.setString(4, aiResult);
            ps.setBoolean(5, hasHair);
            ps.setString(6, imagePath);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) return null;
        return key.longValue();
    }

    private static class VomitStatus {
        private final Long vomitStatusId;
        private final String severityLevel;
        private final String guideText;

        private VomitStatus(Long vomitStatusId, String severityLevel, String guideText) {
            this.vomitStatusId = vomitStatusId;
            this.severityLevel = severityLevel;
            this.guideText = guideText;
        }
    }
}

