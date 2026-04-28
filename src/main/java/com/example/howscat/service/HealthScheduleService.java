package com.example.howscat.service;

import com.example.howscat.dto.HealthScheduleCreateRequest;
import com.example.howscat.dto.HealthScheduleItem;
import com.example.howscat.dto.HealthScheduleUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class HealthScheduleService {

    private final JdbcTemplate jdbcTemplate;
    private final CatOwnershipService catOwnershipService;

    public List<HealthScheduleItem> listSchedules(Long catId, Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        catOwnershipService.assertOwner(catId, userId);

        // cat / birth date
        CatInfo cat = queryCatInfo(catId);

        String sql =
                "SELECT hs.health_schedule_id, hs.health_type_id, hs.next_date, " +
                        "       hs.alarm_enabled, hs.custom_cycle_month, " +
                        "       ht.name AS health_type_name, ht.default_cycle_month " +
                        "FROM health_schedule hs " +
                        "JOIN health_type ht ON ht.health_type_id = hs.health_type_id " +
                        "WHERE hs.cat_id = ? " +
                        "ORDER BY hs.next_date ASC";

        return jdbcTemplate.query(
                sql,
                new Object[]{catId},
                (rs, i) -> {
                    Long scheduleId = rs.getLong("health_schedule_id");
                    Long healthTypeId = rs.getLong("health_type_id");
                    Date nextDateSql = rs.getDate("next_date");
                    LocalDate nextDate = nextDateSql != null ? nextDateSql.toLocalDate() : null;

                    Boolean alarmEnabled = rs.getInt("alarm_enabled") == 1;
                    Integer customCycleMonth = (Integer) rs.getObject("custom_cycle_month");
                    if (customCycleMonth != null && customCycleMonth <= 0) customCycleMonth = null;

                    String healthTypeName = rs.getString("health_type_name");
                    Integer defaultCycleMonth = rs.getInt("default_cycle_month");

                    Integer effectiveCycleMonth = resolveEffectiveCycleMonth(
                            healthTypeName,
                            defaultCycleMonth,
                            customCycleMonth,
                            cat.birthDate
                    );

                    // (현재 DB는 next_date가 있는 경우가 많지만, null이면 안전하게 오늘로 둠)
                    if (nextDate == null) nextDate = LocalDate.now();

                    return new HealthScheduleItem(
                            scheduleId,
                            healthTypeId,
                            healthTypeName,
                            cat.catName,
                            nextDate.toString(),
                            effectiveCycleMonth,
                            alarmEnabled,
                            customCycleMonth
                    );
                }
        );
    }

    public void updateSchedule(
            Long catId,
            Long scheduleId,
            HealthScheduleUpdateRequest request,
            Authentication authentication
    ) {
        Integer userId = (Integer) authentication.getPrincipal();
        catOwnershipService.assertOwner(catId, userId);

        // schedule belongs to cat
        jdbcTemplate.update(
                "UPDATE health_schedule hs SET " +
                        "  custom_cycle_month = COALESCE(?, hs.custom_cycle_month), " +
                        "  alarm_enabled = COALESCE(?, hs.alarm_enabled), " +
                        "  next_date = COALESCE(?, hs.next_date) " +
                        "WHERE hs.health_schedule_id = ? AND hs.cat_id = ?",
                request.getCustomCycleMonth(),
                request.getAlarmEnabled() != null ? (request.getAlarmEnabled() ? 1 : 0) : null,
                request.getNextDate() != null ? Date.valueOf(request.getNextDate()) : null,
                scheduleId,
                catId
        );

        // next_date가 명시된 경우 last_date를 오늘로 갱신 (다음 주기 기산점)
        if (request.getNextDate() != null) {
            jdbcTemplate.update(
                    "UPDATE health_schedule SET last_date = CURDATE() " +
                            "WHERE health_schedule_id = ? AND cat_id = ?",
                    scheduleId, catId
            );
        }

        // 커스텀 주기를 넣었는데 next_date가 비어있다면 last_date 기반으로 보정
        if (request.getNextDate() == null && request.getCustomCycleMonth() != null) {
            jdbcTemplate.update(
                    "UPDATE health_schedule hs " +
                            "SET next_date = DATE_ADD(hs.last_date, INTERVAL ? MONTH) " +
                            "WHERE hs.health_schedule_id = ? AND hs.cat_id = ?",
                    request.getCustomCycleMonth(),
                    scheduleId,
                    catId
            );
        }

        // notification 테이블 sync (가능하면)
        syncNotificationRow(catId, scheduleId, authentication);
    }

    /**
     * 건강검진/예방접종 일정 신규 등록 또는 갱신 (upsert).
     * 같은 cat_id + health_type_id 조합이 이미 있으면 UPDATE, 없으면 INSERT.
     * nextDate = lastDate + effectiveCycleMonth 으로 자동 계산.
     */
    public HealthScheduleItem createOrUpdateSchedule(
            Long catId,
            HealthScheduleCreateRequest request,
            Authentication authentication
    ) {
        Integer userId = (Integer) authentication.getPrincipal();
        catOwnershipService.assertOwner(catId, userId);

        if (request.getHealthTypeId() == null) {
            throw new IllegalArgumentException("healthTypeId is required");
        }

        CatInfo cat = queryCatInfo(catId);

        // healthType 정보 조회
        String healthTypeName = jdbcTemplate.query(
                "SELECT name FROM health_type WHERE health_type_id = ?",
                new Object[]{request.getHealthTypeId()},
                rs -> rs.next() ? rs.getString("name") : "기타"
        );
        Integer defaultCycleMonth = jdbcTemplate.query(
                "SELECT default_cycle_month FROM health_type WHERE health_type_id = ?",
                new Object[]{request.getHealthTypeId()},
                rs -> rs.next() ? rs.getInt("default_cycle_month") : 12
        );

        Integer effectiveCycle = resolveEffectiveCycleMonth(
                healthTypeName,
                defaultCycleMonth,
                request.getCustomCycleMonth(),
                cat.birthDate
        );

        LocalDate lastDate = request.getLastDate() != null
                ? LocalDate.parse(request.getLastDate())
                : LocalDate.now();
        LocalDate nextDate = lastDate.plusMonths(effectiveCycle != null ? effectiveCycle : 12);

        boolean alarmEnabled = Boolean.TRUE.equals(request.getAlarmEnabled());

        // upsert: 같은 cat_id + health_type_id 있으면 UPDATE, 없으면 INSERT
        Long existingId = jdbcTemplate.query(
                "SELECT health_schedule_id FROM health_schedule WHERE cat_id = ? AND health_type_id = ? LIMIT 1",
                new Object[]{catId, request.getHealthTypeId()},
                rs -> rs.next() ? rs.getLong("health_schedule_id") : null
        );

        Long scheduleId;
        if (existingId != null) {
            jdbcTemplate.update(
                    "UPDATE health_schedule SET last_date = ?, next_date = ?, alarm_enabled = ?, " +
                    "custom_cycle_month = ? WHERE health_schedule_id = ?",
                    Date.valueOf(lastDate),
                    Date.valueOf(nextDate),
                    alarmEnabled ? 1 : 0,
                    request.getCustomCycleMonth(),
                    existingId
            );
            scheduleId = existingId;
        } else {
            org.springframework.jdbc.support.GeneratedKeyHolder keyHolder =
                    new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                java.sql.PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO health_schedule (cat_id, health_type_id, last_date, next_date, alarm_enabled, custom_cycle_month) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                        java.sql.Statement.RETURN_GENERATED_KEYS
                );
                ps.setLong(1, catId);
                ps.setLong(2, request.getHealthTypeId());
                ps.setDate(3, Date.valueOf(lastDate));
                ps.setDate(4, Date.valueOf(nextDate));
                ps.setInt(5, alarmEnabled ? 1 : 0);
                if (request.getCustomCycleMonth() != null) ps.setInt(6, request.getCustomCycleMonth());
                else ps.setNull(6, java.sql.Types.INTEGER);
                return ps;
            }, keyHolder);
            Number key = keyHolder.getKey();
            scheduleId = key != null ? key.longValue() : null;
        }

        if (scheduleId != null) {
            syncNotificationRow(catId, scheduleId, authentication);
        }

        return new HealthScheduleItem(
                scheduleId,
                request.getHealthTypeId(),
                healthTypeName,
                cat.catName,
                nextDate.toString(),
                effectiveCycle,
                alarmEnabled,
                request.getCustomCycleMonth()
        );
    }

    /**
     * 건강검진/예방접종 일정 삭제.
     */
    public void deleteSchedule(Long catId, Long scheduleId, Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        catOwnershipService.assertOwner(catId, userId);

        int deleted = jdbcTemplate.update(
                "DELETE FROM health_schedule WHERE health_schedule_id = ? AND cat_id = ?",
                scheduleId, catId
        );
        if (deleted <= 0) {
            throw new IllegalArgumentException("health_schedule not found: " + scheduleId);
        }

        // 연관 notification 행도 정리
        try {
            jdbcTemplate.update(
                    "DELETE FROM notification WHERE related_type = 'HEALTH_SCHEDULE' AND related_id = ? AND cat_id = ?",
                    scheduleId, catId
            );
        } catch (Exception ignored) {}
    }

    private void syncNotificationRow(Long catId, Long scheduleId, Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();

        String sql = ""
                + "SELECT hs.health_schedule_id, hs.next_date, hs.alarm_enabled, "
                + "       ht.name AS health_type_name "
                + "FROM health_schedule hs "
                + "JOIN health_type ht ON ht.health_type_id = hs.health_type_id "
                + "WHERE hs.health_schedule_id = ? AND hs.cat_id = ?";

        HealthScheduleRow row = jdbcTemplate.query(
                sql,
                new Object[]{scheduleId, catId},
                rs -> rs.next() ? new HealthScheduleRow(
                        rs.getLong("health_schedule_id"),
                        rs.getDate("next_date") != null ? rs.getDate("next_date").toLocalDate() : LocalDate.now(),
                        rs.getInt("alarm_enabled") == 1,
                        rs.getString("health_type_name")
                ) : null
        );
        if (row == null) return;

        // scheduled_at: next_date 09:00
        LocalDate d = row.nextDate != null ? row.nextDate : LocalDate.now();
        java.time.LocalDateTime scheduledAt = d.atTime(9, 0);

        String title = row.healthTypeName;
        String content = row.healthTypeName + " 알림";

        // upsert notification
        Integer updated = jdbcTemplate.update(
                "UPDATE notification " +
                        "SET title = ?, content = ?, scheduled_at = ?, is_sent = 0, sent_at = NULL " +
                        "WHERE user_id = ? AND cat_id = ? AND related_type = ? AND related_id = ?",
                title,
                content,
                java.sql.Timestamp.valueOf(scheduledAt),
                userId,
                catId,
                "HEALTH_SCHEDULE",
                scheduleId
        );

        if (updated == null || updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO notification (user_id, cat_id, related_type, related_id, title, content, scheduled_at, is_sent, sent_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, 0, NULL)",
                    userId,
                    catId,
                    "HEALTH_SCHEDULE",
                    scheduleId,
                    title,
                    content,
                    java.sql.Timestamp.valueOf(scheduledAt)
            );
        }
    }

    private Integer resolveEffectiveCycleMonth(
            String healthTypeName,
            Integer defaultCycleMonth,
            Integer customCycleMonth,
            LocalDate catBirthDate
    ) {
        if (customCycleMonth != null && customCycleMonth > 0) return customCycleMonth;

        String name = healthTypeName != null ? healthTypeName.toLowerCase(Locale.getDefault()) : "";

        // 건강검진: 7세 기준 12개월/6개월
        if (name.contains("검진")) {
            int ageYears = computeAgeYears(catBirthDate, LocalDate.now());
            return ageYears < 7 ? 12 : 6;
        }

        // 예방접종: 12개월
        if (name.contains("접종") || name.contains("예방")) {
            return 12;
        }

        return defaultCycleMonth;
    }

    private int computeAgeYears(LocalDate birth, LocalDate now) {
        if (birth == null) return 0;
        return Math.max(0, Period.between(birth, now).getYears());
    }

    private CatInfo queryCatInfo(Long catId) {
        String sql = "SELECT name, birth_date FROM cat WHERE cat_id = ?";
        return jdbcTemplate.query(
                sql,
                new Object[]{catId},
                rs -> rs.next() ? new CatInfo(
                        rs.getString("name"),
                        rs.getDate("birth_date") != null ? rs.getDate("birth_date").toLocalDate() : null
                ) : new CatInfo("고양이", null)
        );
    }

    private static class CatInfo {
        private final String catName;
        private final LocalDate birthDate;

        private CatInfo(String catName, LocalDate birthDate) {
            this.catName = catName;
            this.birthDate = birthDate;
        }
    }

    private static class HealthScheduleRow {
        private final Long scheduleId;
        private final LocalDate nextDate;
        private final Boolean alarmEnabled;
        private final String healthTypeName;

        private HealthScheduleRow(Long scheduleId, LocalDate nextDate, Boolean alarmEnabled, String healthTypeName) {
            this.scheduleId = scheduleId;
            this.nextDate = nextDate;
            this.alarmEnabled = alarmEnabled;
            this.healthTypeName = healthTypeName;
        }
    }
}

