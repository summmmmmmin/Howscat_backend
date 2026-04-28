package com.example.howscat.service;

import com.example.howscat.dto.CareWeightRequest;
import com.example.howscat.dto.ObesityCheckRequest;
import com.example.howscat.dto.ObesityCheckResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ObesityCheckService {

    private final JdbcTemplate jdbcTemplate;

    public ObesityCheckResponse analyzeAndSave(Long catId,
                                                 ObesityCheckRequest request,
                                                 Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        assertCatBelongsToUser(catId, userId);

        double bodyFatPercent = nvl(request.getBodyFatPercent());
        double weightKg = nvl(request.getWeightKg());
        double feedKcalPerG = nvl(request.getFeedKcalPerG());

        if (feedKcalPerG <= 0) {
            throw new IllegalArgumentException("feedKcalPerG must be > 0");
        }

        String obesityLevel = classify(bodyFatPercent);

        // 사용자 기준: RER = 체중(kg) * 30 + 70
        double rer = (weightKg * 30.0) + 70.0;

        // recommended food kcal based on obesity level
        double recommendedFoodKcal;
        if ("UNDERWEIGHT".equals(obesityLevel)) {
            recommendedFoodKcal = rer * 1.4;
        } else if ("NORMAL".equals(obesityLevel)) {
            recommendedFoodKcal = rer * 1.3;
        } else if ("SLIGHTLY_OVERWEIGHT".equals(obesityLevel)) {
            recommendedFoodKcal = rer * 1.0;
        } else {
            recommendedFoodKcal = rer * 0.9;
        }

        // DB 컬럼 단위(권장 사료량)는 UI에 바로 쓰기 위해 g로 저장
        double recommendedFoodG = recommendedFoodKcal / feedKcalPerG;
        double recommendedWaterMl = weightKg * 50.0;

        // lean mass = kg * (1 - bodyFat%)
        double lean = weightKg * (1.0 - bodyFatPercent / 100.0);
        double recommendedTargetWeight = lean / 0.8;

        Long weightRecordId = upsertTodayWeightRecord(catId, weightKg);
        Long obesityCheckId = insertObesityCheck(catId,
                weightRecordId,
                obesityLevel,
                bodyFatPercent,
                recommendedTargetWeight,
                recommendedWaterMl,
                recommendedFoodG);

        // calendar_memo에 기록(health_type_id를 name 기반으로 찾아 없으면 스킵)
        Long healthTypeId = findHealthTypeIdByKeywords("비만");
        String obesityMemo = "비만도 | " + obesityLevel + " | 체지방률 " + bodyFatPercent + "% | 목표 "
                + String.format(Locale.getDefault(), "%.2f", recommendedTargetWeight) + "kg";
        if (healthTypeId != null) {
            insertCalendarMemo(userId.intValue(), catId, healthTypeId, obesityMemo, LocalDate.now());
        } else {
            insertCalendarMemoWithoutHealthType(userId, catId, obesityMemo, LocalDate.now());
        }

        return new ObesityCheckResponse(
                obesityLevel,
                bodyFatPercent,
                recommendedTargetWeight,
                recommendedWaterMl,
                recommendedFoodG
        );
    }

    /**
     * 물·사료 지급량 계산 시 입력한 몸무게를 weight_record + calendar_memo에 반영
     */
    public void recordCareWeight(Long catId, CareWeightRequest req, Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        assertCatBelongsToUser(catId, userId);
        if (req == null || req.getWeightKg() == null || req.getWeightKg() <= 0) {
            throw new IllegalArgumentException("weightKg is required");
        }
        LocalDate d = LocalDate.now();
        if (req.getMemoDate() != null && !req.getMemoDate().isBlank()) {
            d = LocalDate.parse(req.getMemoDate());
        }
        double water = req.getWaterMl() != null ? req.getWaterMl() : 0;
        double food = req.getFoodG() != null ? req.getFoodG() : 0;
        upsertWeightRecordForDate(catId, req.getWeightKg(), d, water, food);
        // 몸무게 기록은 weight_record 이벤트로 캘린더에 표시하므로 일반 메모 중복 저장을 하지 않음.
    }

    private void upsertWeightRecordForDate(Long catId, double weightKg, LocalDate date,
                                           double waterMl, double foodG) {
        Long existingId = jdbcTemplate.query(
                "SELECT weight_record_id FROM weight_record WHERE cat_id = ? AND DATE(recorded_at) = ? ORDER BY recorded_at DESC LIMIT 1",
                new Object[]{catId, date},
                rs -> rs.next() ? rs.getLong("weight_record_id") : null
        );
        if (existingId != null) {
            jdbcTemplate.update(
                    "UPDATE weight_record SET weight = ?, recommended_water_ml = ?, recommended_food_g = ? WHERE weight_record_id = ?",
                    weightKg, waterMl > 0 ? waterMl : null, foodG > 0 ? foodG : null, existingId
            );
            return;
        }
        Timestamp ts = Timestamp.valueOf(date.atStartOfDay());
        jdbcTemplate.update(
                "INSERT INTO weight_record (cat_id, weight, recorded_at, recommended_water_ml, recommended_food_g) VALUES (?, ?, ?, ?, ?)",
                catId, weightKg, ts, waterMl > 0 ? waterMl : null, foodG > 0 ? foodG : null
        );
    }

    public List<com.example.howscat.dto.WeightHistoryItem> listWeightHistory(
            Long catId,
            Integer limit,
            Authentication authentication
    ) {
        Integer userId = (Integer) authentication.getPrincipal();
        assertCatBelongsToUser(catId, userId);

        int l = limit != null && limit > 0 ? limit : 7;

        String sql = ""
                + "SELECT t.recorded_at, t.weight, t.recommended_water_ml, t.recommended_food_g "
                + "FROM ( "
                + "  SELECT recorded_at, weight, recommended_water_ml, recommended_food_g "
                + "  FROM weight_record "
                + "  WHERE cat_id = ? "
                + "  ORDER BY recorded_at DESC "
                + "  LIMIT ? "
                + ") t "
                + "ORDER BY t.recorded_at ASC";

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        List<com.example.howscat.dto.WeightHistoryItem> items = new ArrayList<>();
        jdbcTemplate.query(sql, new Object[]{catId, l}, rs -> {
            LocalDateTime dt = rs.getTimestamp("recorded_at").toLocalDateTime();
            double wMl = rs.getDouble("recommended_water_ml");
            boolean wNull = rs.wasNull();
            double fG  = rs.getDouble("recommended_food_g");
            boolean fNull = rs.wasNull();
            items.add(new com.example.howscat.dto.WeightHistoryItem(
                    fmt.format(dt),
                    rs.getDouble("weight"),
                    wNull ? null : wMl,
                    fNull ? null : fG
            ));
        });
        return items;
    }

    public List<com.example.howscat.dto.ObesityHistoryItem> listObesityHistory(
            Long catId,
            Integer limit,
            Authentication authentication
    ) {
        Integer userId = (Integer) authentication.getPrincipal();
        assertCatBelongsToUser(catId, userId);

        int l = limit != null && limit > 0 ? limit : 7;

        String sql = ""
                + "SELECT t.checked_at, t.obesity_level, t.body_fat_percent, "
                + "       t.recommended_target_weight, t.recommended_water, t.recommended_food "
                + "FROM ( "
                + "  SELECT checked_at, obesity_level, body_fat_percent, recommended_target_weight, "
                + "         recommended_water, recommended_food "
                + "  FROM obesity_check_record "
                + "  WHERE cat_id = ? "
                + "  ORDER BY checked_at DESC "
                + "  LIMIT ? "
                + ") t "
                + "ORDER BY t.checked_at ASC";

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        List<com.example.howscat.dto.ObesityHistoryItem> items = new ArrayList<>();
        jdbcTemplate.query(sql, new Object[]{catId, l}, rs -> {
            LocalDateTime dt = rs.getTimestamp("checked_at").toLocalDateTime();
            items.add(new com.example.howscat.dto.ObesityHistoryItem(
                    fmt.format(dt),
                    rs.getString("obesity_level"),
                    rs.getDouble("body_fat_percent"),
                    rs.getDouble("recommended_target_weight"),
                    rs.getDouble("recommended_water"),
                    rs.getDouble("recommended_food")
            ));
        });
        return items;
    }

    private void assertCatBelongsToUser(Long catId, Integer userId) {
        Long ownerUserId = jdbcTemplate.query(
                "SELECT user_id FROM cat WHERE cat_id = ?",
                new Object[]{catId},
                rs -> rs.next() ? rs.getLong("user_id") : null
        );
        if (ownerUserId == null) {
            throw new IllegalArgumentException("cat not found");
        }
        if (ownerUserId.longValue() != userId.longValue()) {
            throw new SecurityException("cat does not belong to user");
        }
    }

    private double nvl(Double v) {
        if (v == null) throw new IllegalArgumentException("value is required");
        return v;
    }

    private String classify(double bodyFatPercent) {
        if (bodyFatPercent < 16) return "UNDERWEIGHT";
        if (bodyFatPercent <= 25) return "NORMAL";
        if (bodyFatPercent <= 35) return "SLIGHTLY_OVERWEIGHT";
        return "OVERWEIGHT";
    }

    private Long upsertTodayWeightRecord(Long catId, double weightKg) {
        LocalDate today = LocalDate.now();
        // 오늘 기록이 있으면 재사용
        Long existingId = jdbcTemplate.query(
                "SELECT weight_record_id FROM weight_record WHERE cat_id = ? AND DATE(recorded_at) = ? ORDER BY recorded_at DESC LIMIT 1",
                new Object[]{catId, today},
                rs -> rs.next() ? rs.getLong("weight_record_id") : null
        );
        if (existingId != null) return existingId;

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO weight_record (cat_id, weight, recorded_at) VALUES (?, ?, NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, catId);
            ps.setDouble(2, weightKg);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("failed to insert weight_record");
        return key.longValue();
    }

    private Long insertObesityCheck(Long catId,
                                     Long weightRecordId,
                                     String obesityLevel,
                                     double bodyFatPercent,
                                     double recommendedTargetWeight,
                                     double recommendedWaterMl,
                                     double recommendedFoodG) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO obesity_check_record " +
                            "(cat_id, weight_record_id, obesity_level, body_fat_percent, recommended_target_weight, recommended_water, recommended_food, checked_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, catId);
            ps.setLong(2, weightRecordId);
            ps.setString(3, obesityLevel);
            ps.setDouble(4, bodyFatPercent);
            ps.setDouble(5, recommendedTargetWeight);
            ps.setDouble(6, recommendedWaterMl);
            ps.setDouble(7, recommendedFoodG);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) return null;
        return key.longValue();
    }

    private Long findHealthTypeIdByKeywords(String keyword) {
        String sql = "SELECT health_type_id FROM health_type WHERE LOWER(name) LIKE ? LIMIT 1";
        String like = "%" + keyword.toLowerCase(Locale.getDefault()) + "%";
        return jdbcTemplate.query(sql, new Object[]{like}, rs -> rs.next() ? rs.getLong("health_type_id") : null);
    }

    private void insertCalendarMemo(int userId,
                                      Long catId,
                                      Long healthTypeId,
                                      String content,
                                      LocalDate date) {
        jdbcTemplate.update(
                "INSERT INTO calendar_memo (content, memo_date, cat_id, health_type_id, user_id) VALUES (?, ?, ?, ?, ?)",
                content, date, catId, healthTypeId, userId
        );
    }

    private void insertCalendarMemoWithoutHealthType(int userId,
                                                     Long catId,
                                                     String content,
                                                     LocalDate date) {
        jdbcTemplate.update(
                "INSERT INTO calendar_memo (content, memo_date, cat_id, health_type_id, user_id) VALUES (?, ?, ?, NULL, ?)",
                content, date, catId, userId
        );
    }
}

