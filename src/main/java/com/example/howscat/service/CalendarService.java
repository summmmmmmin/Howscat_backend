package com.example.howscat.service;

import com.example.howscat.dto.CalendarEventItem;
import com.example.howscat.dto.CalendarMemoCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final JdbcTemplate jdbcTemplate;

    public List<CalendarEventItem> listCalendarEvents(Long catId, LocalDate from, LocalDate to,
                                                       Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        assertCatBelongsToUser(catId, userId);

        // ── 기존 핵심 쿼리 (항상 실행) ─────────────────────────────────────────
        String baseSql = ""
                + "SELECT * FROM ( "
                + "  SELECT cm.calendar_memo_id AS event_id, "
                + "         'MEMO' AS event_type, "
                + "         DATE(cm.memo_date) AS event_date, "
                + "         NULL AS event_time, "
                + "         COALESCE(ht.name, '메모') AS title, "
                + "         cm.content AS subtitle, "
                + "         NULL AS image_path, "
                + "         NULL AS risk_level, "
                + "         NULL AS vomit_color, "
                + "         NULL AS guide_text, "
                + "         NULL AS schedule_memo, "
                + "         NULL AS alarm_enabled, "
                + "         NULL AS linked_memo_id, "
                + "         cm.health_type_id AS health_type_id "
                + "  FROM calendar_memo cm "
                + "  LEFT JOIN health_type ht ON ht.health_type_id = cm.health_type_id "
                + "  WHERE cm.cat_id = ? "
                + "    AND DATE(cm.memo_date) BETWEEN ? AND ? "
                + "    AND cm.content NOT LIKE '지급량 계산 · 몸무게 %' "
                + "    AND (cm.health_type_id IS NULL OR cm.health_type_id NOT IN (1, 2)) "
                + "  UNION ALL "
                // 완료된 건강 일정 (last_date 기준)
                + "  SELECT hs.health_schedule_id AS event_id, "
                + "         CASE "
                + "           WHEN LOWER(ht.name) LIKE '%검진%' THEN 'HEALTH_CHECKUP' "
                + "           ELSE 'HEALTH_VACCINE' "
                + "         END AS event_type, "
                + "         hs.last_date AS event_date, "
                + "         NULL AS event_time, "
                + "         CONCAT(ht.name, ' 완료') AS title, "
                + "         CONCAT(c.name, ' · 다음: ', COALESCE(hs.next_date, '-')) AS subtitle, "
                + "         NULL AS image_path, "
                + "         NULL AS risk_level, "
                + "         NULL AS vomit_color, "
                + "         NULL AS guide_text, "
                + "         NULL AS schedule_memo, "
                + "         hs.alarm_enabled AS alarm_enabled, "
                + "         NULL AS linked_memo_id, "
                + "         hs.health_type_id AS health_type_id "
                + "  FROM health_schedule hs "
                + "  JOIN health_type ht ON ht.health_type_id = hs.health_type_id "
                + "  JOIN cat c ON c.cat_id = hs.cat_id "
                + "  WHERE hs.cat_id = ? "
                + "    AND hs.last_date IS NOT NULL "
                + "    AND hs.last_date BETWEEN ? AND ? "
                + "  UNION ALL "
                // 예정된 건강 일정 (next_date 기준)
                + "  SELECT hs.health_schedule_id AS event_id, "
                + "         CASE "
                + "           WHEN LOWER(ht.name) LIKE '%검진%' THEN 'HEALTH_CHECKUP' "
                + "           ELSE 'HEALTH_VACCINE' "
                + "         END AS event_type, "
                + "         hs.next_date AS event_date, "
                + "         NULL AS event_time, "
                + "         CONCAT(ht.name, ' 예정') AS title, "
                + "         CASE WHEN hs.alarm_enabled = 1 THEN '자동 알림' ELSE '알림 없음' END AS subtitle, "
                + "         NULL AS image_path, "
                + "         NULL AS risk_level, "
                + "         NULL AS vomit_color, "
                + "         NULL AS guide_text, "
                + "         cm2.content AS schedule_memo, "
                + "         hs.alarm_enabled AS alarm_enabled, "
                + "         cm2.calendar_memo_id AS linked_memo_id, "
                + "         hs.health_type_id AS health_type_id "
                + "  FROM health_schedule hs "
                + "  JOIN health_type ht ON ht.health_type_id = hs.health_type_id "
                + "  JOIN cat c ON c.cat_id = hs.cat_id "
                + "  LEFT JOIN calendar_memo cm2 ON cm2.cat_id = hs.cat_id "
                + "    AND DATE(cm2.memo_date) = hs.next_date "
                + "    AND cm2.health_type_id = hs.health_type_id "
                + "  WHERE hs.cat_id = ? "
                + "    AND hs.next_date BETWEEN ? AND ? "
                + "  UNION ALL "
                + "  SELECT wr.weight_record_id AS event_id, "
                + "         'WEIGHT' AS event_type, "
                + "         DATE(wr.recorded_at) AS event_date, "
                + "         TIME_FORMAT(wr.recorded_at, '%H:%i') AS event_time, "
                + "         '몸무게 기록' AS title, "
                + "         CONCAT(FORMAT(wr.weight, 2), 'kg') AS subtitle, "
                + "         NULL AS image_path, "
                + "         NULL AS risk_level, "
                + "         NULL AS vomit_color, "
                + "         NULL AS guide_text, "
                + "         NULL AS schedule_memo, "
                + "         NULL AS alarm_enabled, "
                + "         NULL AS linked_memo_id, "
                + "         NULL AS health_type_id "
                + "  FROM weight_record wr "
                + "  WHERE wr.cat_id = ? "
                + "    AND DATE(wr.recorded_at) BETWEEN ? AND ? "
                + "  UNION ALL "
                + "  SELECT vr.vomit_record_id AS event_id, "
                + "         'VOMIT' AS event_type, "
                + "         DATE(vr.created_at) AS event_date, "
                + "         TIME_FORMAT(vr.created_at, '%H:%i') AS event_time, "
                + "         '토 분석 기록' AS title, "
                + "         COALESCE("
                + "           CONCAT(COALESCE(vr.ai_result, CONCAT('위험도 ', vs.severity_level)), ' · ', COALESCE(vr.memo, '메모 없음')),"
                + "           CONCAT('위험도 미상 · ', COALESCE(vr.memo, '메모 없음'))"
                + "         ) AS subtitle, "
                + "         vr.image_path AS image_path, "
                + "         vs.severity_level AS risk_level, "
                + "         vs.color AS vomit_color, "
                + "         vs.guide_text AS guide_text, "
                + "         NULL AS schedule_memo, "
                + "         NULL AS alarm_enabled, "
                + "         NULL AS linked_memo_id, "
                + "         NULL AS health_type_id "
                + "  FROM vomit_record vr "
                + "  LEFT JOIN vomit_status vs ON vs.vomit_status_id = vr.vomit_status_id "
                + "  WHERE vr.cat_id = ? "
                + "    AND DATE(vr.created_at) BETWEEN ? AND ? "
                + ") t "
                + "ORDER BY t.event_date ASC, t.event_time ASC";

        List<CalendarEventItem> result = new ArrayList<>(jdbcTemplate.query(
                baseSql,
                // MEMO(3), HEALTH_CHECKUP last_date(3), HEALTH_CHECKUP next_date(3), WEIGHT(3), VOMIT(3) = 15
                new Object[]{catId, from, to, catId, from, to, catId, from, to, catId, from, to, catId, from, to},
                CalendarService::mapCalendarEventRow
        ));

        // ── 케어 기록 쿼리 (테이블 없으면 조용히 스킵) ────────────────────────
        result.addAll(queryMedication(catId, from, to));
        result.addAll(queryLitter(catId, from, to));
        result.addAll(queryVetVisit(catId, from, to));

        result.sort(Comparator.comparing(
                (CalendarEventItem e) -> e.getDate() != null ? e.getDate() : "",
                Comparator.naturalOrder()
        ));
        return result;
    }

    private List<CalendarEventItem> queryMedication(Long catId, LocalDate from, LocalDate to) {
        try {
            return jdbcTemplate.query(
                    "SELECT medication_id, start_date, name, dosage, frequency, alarm_enabled, notes " +
                            "FROM medication WHERE cat_id = ? AND DATE(start_date) BETWEEN ? AND ?",
                    new Object[]{catId, from, to},
                    (rs, i) -> new CalendarEventItem(
                            rs.getLong("medication_id"),
                            "MEDICATION",
                            rs.getDate("start_date") != null ? rs.getDate("start_date").toString() : null,
                            null,
                            "투약 기록",
                            buildMedSubtitle(rs.getString("name"), rs.getString("dosage"), rs.getString("frequency")),
                            null, null, null,
                            rs.getString("notes"),
                            null,
                            rs.getInt("alarm_enabled") == 1,
                            null, null
                    )
            );
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<CalendarEventItem> queryLitter(Long catId, LocalDate from, LocalDate to) {
        try {
            return jdbcTemplate.query(
                    "SELECT litter_box_record_id, record_date, count, color, shape, abnormal, notes " +
                            "FROM litter_box_record WHERE cat_id = ? AND DATE(record_date) BETWEEN ? AND ?",
                    new Object[]{catId, from, to},
                    (rs, i) -> new CalendarEventItem(
                            rs.getLong("litter_box_record_id"),
                            "LITTER",
                            rs.getDate("record_date") != null ? rs.getDate("record_date").toString() : null,
                            null,
                            "화장실 기록",
                            buildLitterSubtitle(rs.getInt("count"), rs.getString("color"),
                                    rs.getString("shape"), rs.getInt("abnormal") == 1),
                            null, null, null,
                            rs.getString("notes"),
                            null, null, null, null
                    )
            );
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<CalendarEventItem> queryVetVisit(Long catId, LocalDate from, LocalDate to) {
        try {
            return jdbcTemplate.query(
                    "SELECT vet_visit_id, visit_date, hospital_name, diagnosis, notes " +
                            "FROM vet_visit WHERE cat_id = ? AND DATE(visit_date) BETWEEN ? AND ?",
                    new Object[]{catId, from, to},
                    (rs, i) -> {
                        String hospital = rs.getString("hospital_name");
                        String diagnosis = rs.getString("diagnosis");
                        String subtitle = (hospital != null ? hospital : "병원")
                                + (diagnosis != null && !diagnosis.isBlank() ? " · " + diagnosis : "");
                        return new CalendarEventItem(
                                rs.getLong("vet_visit_id"),
                                "VET_VISIT",
                                rs.getDate("visit_date") != null ? rs.getDate("visit_date").toString() : null,
                                null,
                                "진료 기록",
                                subtitle,
                                null, null, null,
                                rs.getString("notes"),
                                null, null, null, null
                        );
                    }
            );
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String buildMedSubtitle(String name, String dosage, String frequency) {
        String freq = "TWICE_DAILY".equals(frequency) ? "하루 2회"
                : "AS_NEEDED".equals(frequency) ? "필요시" : "하루 1회";
        return (name != null ? name : "약")
                + (dosage != null && !dosage.isBlank() ? " · " + dosage : "")
                + " · " + freq;
    }

    private static String buildLitterSubtitle(int count, String color, String shape, boolean abnormal) {
        String c = "YELLOW".equals(color) ? "노란색" : "RED".equals(color) ? "붉은색"
                : "OTHER".equals(color) ? "기타색" : "정상색";
        String s = "SOFT".equals(shape) ? "무른 변" : "LIQUID".equals(shape) ? "액체 변"
                : "NONE".equals(shape) ? "변 없음" : "정상 변";
        return count + "회 · " + c + " · " + s + (abnormal ? " · 이상 증상" : "");
    }

    private static CalendarEventItem mapCalendarEventRow(ResultSet rs, int i) throws SQLException {
        return new CalendarEventItem(
                rs.getLong("event_id"),
                rs.getString("event_type"),
                rs.getString("event_date"),
                rs.getString("event_time"),
                rs.getString("title"),
                rs.getString("subtitle"),
                rs.getString("image_path"),
                rs.getString("risk_level"),
                rs.getString("vomit_color"),
                rs.getString("guide_text"),
                rs.getString("schedule_memo"),
                readNullableBoolean(rs, "alarm_enabled"),
                readNullableLong(rs, "linked_memo_id"),
                readNullableLong(rs, "health_type_id")
        );
    }

    private static Boolean readNullableBoolean(ResultSet rs, String col) throws SQLException {
        Object o = rs.getObject(col);
        if (o == null) return null;
        if (o instanceof Boolean) return (Boolean) o;
        return ((Number) o).intValue() != 0;
    }

    private static Long readNullableLong(ResultSet rs, String col) throws SQLException {
        Object o = rs.getObject(col);
        if (o == null) return null;
        return ((Number) o).longValue();
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

    public void updateCalendarMemo(Long catId,
                                     Long memoId,
                                     String content,
                                     String memoDate,
                                     Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();

        String c = content != null ? content : "";
        c = c.trim();
        if (c.isEmpty()) {
            throw new IllegalArgumentException("content is required");
        }

        int updated;
        if (memoDate != null && !memoDate.isBlank()) {
            LocalDate d = LocalDate.parse(memoDate.trim());
            updated = jdbcTemplate.update(
                    "UPDATE calendar_memo cm "
                            + "SET cm.content = ?, cm.memo_date = ? "
                            + "WHERE cm.calendar_memo_id = ? "
                            + "  AND cm.cat_id = ? "
                            + "  AND EXISTS ( "
                            + "      SELECT 1 FROM cat c "
                            + "      WHERE c.cat_id = cm.cat_id AND c.user_id = ? "
                            + "  )",
                    c,
                    d,
                    memoId,
                    catId,
                    userId
            );
        } else {
            updated = jdbcTemplate.update(
                    "UPDATE calendar_memo cm "
                            + "SET cm.content = ? "
                            + "WHERE cm.calendar_memo_id = ? "
                            + "  AND cm.cat_id = ? "
                            + "  AND EXISTS ( "
                            + "      SELECT 1 FROM cat c "
                            + "      WHERE c.cat_id = cm.cat_id AND c.user_id = ? "
                            + "  )",
                    c,
                    memoId,
                    catId,
                    userId
            );
        }

        if (updated <= 0) {
            throw new IllegalArgumentException("calendar_memo not found");
        }
    }

    public void addCalendarMemo(
            Long catId,
            CalendarMemoCreateRequest request,
            org.springframework.security.core.Authentication authentication
    ) {
        Integer userId = (Integer) authentication.getPrincipal();

        String content = request != null ? request.getContent() : null;
        if (content == null) content = "";
        content = content.trim();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("content is required");
        }

        LocalDate memoDate = LocalDate.now();
        if (request != null && request.getMemoDate() != null && !request.getMemoDate().isBlank()) {
            memoDate = LocalDate.parse(request.getMemoDate());
        }

        Long healthTypeId = request != null ? request.getHealthTypeId() : null;

        jdbcTemplate.update(
                "INSERT INTO calendar_memo (content, memo_date, cat_id, health_type_id, user_id) "
                        + "VALUES (?, ?, ?, ?, ?)",
                content,
                memoDate,
                catId,
                healthTypeId,
                userId
        );
    }

    public void deleteAllCalendarMemos(
            Long catId,
            Authentication authentication
    ) {
        Integer userId = (Integer) authentication.getPrincipal();
        assertCatBelongsToUser(catId, userId);
        jdbcTemplate.update(
                "DELETE FROM calendar_memo WHERE cat_id = ? AND user_id = ?",
                catId, userId
        );
    }

    public void deleteCalendarMemo(
            Long catId,
            Long memoId,
            Authentication authentication
    ) {
        Integer userId = (Integer) authentication.getPrincipal();
        assertCatBelongsToUser(catId, userId);
        int deleted = jdbcTemplate.update(
                "DELETE FROM calendar_memo WHERE calendar_memo_id = ? AND cat_id = ? AND user_id = ?",
                memoId, catId, userId
        );
        if (deleted <= 0) {
            throw new IllegalArgumentException("calendar_memo not found");
        }
    }
}
