package com.example.howscat.service;

import com.example.howscat.dto.LitterBoxCreateRequest;
import com.example.howscat.dto.LitterBoxItem;
import com.example.howscat.dto.MedicationCreateRequest;
import com.example.howscat.dto.MedicationItem;
import com.example.howscat.dto.VetVisitCreateRequest;
import com.example.howscat.dto.VetVisitItem;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CareRecordService {

    private final JdbcTemplate jdbcTemplate;
    private final CatOwnershipService catOwnershipService;

    // ─── Medication ───────────────────────────────────────────────────────────

    public List<MedicationItem> listMedications(Long catId, Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        catOwnershipService.assertOwner(catId, userId);

        return jdbcTemplate.query(
                "SELECT medication_id, name, dosage, frequency, start_date, end_date, " +
                        "alarm_enabled, alarm_hour, alarm_minute, alarm_hour2, alarm_minute2, notes " +
                        "FROM medication WHERE cat_id = ? ORDER BY start_date DESC",
                new Object[]{catId},
                (rs, i) -> new MedicationItem(
                        rs.getLong("medication_id"),
                        rs.getString("name"),
                        rs.getString("dosage"),
                        rs.getString("frequency"),
                        rs.getDate("start_date") != null ? rs.getDate("start_date").toString() : null,
                        rs.getDate("end_date") != null ? rs.getDate("end_date").toString() : null,
                        rs.getInt("alarm_enabled") == 1,
                        rs.getInt("alarm_hour"),
                        rs.getInt("alarm_minute"),
                        (Integer) rs.getObject("alarm_hour2"),
                        (Integer) rs.getObject("alarm_minute2"),
                        rs.getString("notes")
                )
        );
    }

    public void addMedication(Long catId, MedicationCreateRequest req, Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        catOwnershipService.assertOwner(catId, userId);

        LocalDate startDate = req.getStartDate() != null ? LocalDate.parse(req.getStartDate()) : LocalDate.now();
        LocalDate endDate = req.getEndDate() != null && !req.getEndDate().isBlank()
                ? LocalDate.parse(req.getEndDate()) : null;

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO medication (cat_id, user_id, name, dosage, frequency, start_date, end_date, " +
                            "alarm_enabled, alarm_hour, alarm_minute, alarm_hour2, alarm_minute2, notes, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, catId);
            ps.setInt(2, userId);
            ps.setString(3, req.getName());
            ps.setString(4, req.getDosage());
            ps.setString(5, req.getFrequency() != null ? req.getFrequency() : "DAILY");
            ps.setDate(6, Date.valueOf(startDate));
            ps.setObject(7, endDate != null ? Date.valueOf(endDate) : null);
            ps.setInt(8, Boolean.TRUE.equals(req.getAlarmEnabled()) ? 1 : 0);
            ps.setInt(9, req.getAlarmHour() != null ? req.getAlarmHour() : 9);
            ps.setInt(10, req.getAlarmMinute() != null ? req.getAlarmMinute() : 0);
            ps.setObject(11, req.getAlarmHour2());
            ps.setObject(12, req.getAlarmMinute2());
            ps.setString(13, req.getNotes());
            return ps;
        }, keyHolder);

        // 캘린더는 CalendarService UNION 쿼리로 자동 표시되므로 별도 메모 삽입 불필요
    }

    public void updateMedication(Long catId, Long medicationId, MedicationCreateRequest req, Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        catOwnershipService.assertOwner(catId, userId);

        LocalDate startDate = req.getStartDate() != null && !req.getStartDate().isBlank()
                ? LocalDate.parse(req.getStartDate()) : LocalDate.now();
        LocalDate endDate = req.getEndDate() != null && !req.getEndDate().isBlank()
                ? LocalDate.parse(req.getEndDate()) : null;

        jdbcTemplate.update(
                "UPDATE medication SET name=?, dosage=?, frequency=?, start_date=?, end_date=?, " +
                        "alarm_enabled=?, alarm_hour=?, alarm_minute=?, alarm_hour2=?, alarm_minute2=?, notes=? " +
                        "WHERE medication_id=? AND cat_id=?",
                req.getName(),
                req.getDosage(),
                req.getFrequency() != null ? req.getFrequency() : "DAILY",
                Date.valueOf(startDate),
                endDate != null ? Date.valueOf(endDate) : null,
                Boolean.TRUE.equals(req.getAlarmEnabled()) ? 1 : 0,
                req.getAlarmHour() != null ? req.getAlarmHour() : 9,
                req.getAlarmMinute() != null ? req.getAlarmMinute() : 0,
                req.getAlarmHour2(),
                req.getAlarmMinute2(),
                req.getNotes(),
                medicationId, catId
        );
    }

    public void deleteMedication(Long catId, Long medicationId, Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        catOwnershipService.assertOwner(catId, userId);
        jdbcTemplate.update(
                "DELETE FROM medication WHERE medication_id = ? AND cat_id = ?",
                medicationId, catId);
    }

    // ─── Litter Box ───────────────────────────────────────────────────────────

    public List<LitterBoxItem> listLitterRecords(Long catId, Integer limit, Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        catOwnershipService.assertOwner(catId, userId);
        int lim = limit != null && limit > 0 ? limit : 30;

        return jdbcTemplate.query(
                "SELECT litter_box_record_id, record_date, count, color, shape, abnormal, notes " +
                        "FROM litter_box_record WHERE cat_id = ? ORDER BY record_date DESC, litter_box_record_id DESC LIMIT ?",
                new Object[]{catId, lim},
                (rs, i) -> new LitterBoxItem(
                        rs.getLong("litter_box_record_id"),
                        rs.getDate("record_date") != null ? rs.getDate("record_date").toString() : null,
                        rs.getInt("count"),
                        rs.getString("color"),
                        rs.getString("shape"),
                        rs.getInt("abnormal") == 1,
                        rs.getString("notes")
                )
        );
    }

    public void addLitterRecord(Long catId, LitterBoxCreateRequest req, Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        catOwnershipService.assertOwner(catId, userId);

        LocalDate date = req.getDate() != null && !req.getDate().isBlank()
                ? LocalDate.parse(req.getDate()) : LocalDate.now();
        boolean abnormal = Boolean.TRUE.equals(req.getAbnormal());

        jdbcTemplate.update(
                "INSERT INTO litter_box_record (cat_id, user_id, record_date, count, color, shape, abnormal, notes, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                catId, userId,
                Date.valueOf(date),
                req.getCount() != null ? req.getCount() : 1,
                req.getColor() != null ? req.getColor() : "NORMAL",
                req.getShape() != null ? req.getShape() : "NORMAL",
                abnormal ? 1 : 0,
                req.getNotes()
        );

        // 캘린더는 CalendarService UNION 쿼리로 자동 표시
    }

    public void updateLitterRecord(Long catId, Long recordId, LitterBoxCreateRequest req, Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        catOwnershipService.assertOwner(catId, userId);

        LocalDate date = req.getDate() != null && !req.getDate().isBlank()
                ? LocalDate.parse(req.getDate()) : LocalDate.now();

        jdbcTemplate.update(
                "UPDATE litter_box_record SET record_date=?, count=?, color=?, shape=?, abnormal=?, notes=? " +
                        "WHERE litter_box_record_id=? AND cat_id=?",
                Date.valueOf(date),
                req.getCount() != null ? req.getCount() : 1,
                req.getColor() != null ? req.getColor() : "NORMAL",
                req.getShape() != null ? req.getShape() : "NORMAL",
                Boolean.TRUE.equals(req.getAbnormal()) ? 1 : 0,
                req.getNotes(),
                recordId, catId
        );
    }

    public void deleteLitterRecord(Long catId, Long recordId, Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        catOwnershipService.assertOwner(catId, userId);
        jdbcTemplate.update(
                "DELETE FROM litter_box_record WHERE litter_box_record_id = ? AND cat_id = ?",
                recordId, catId);
    }

    // ─── Vet Visit ────────────────────────────────────────────────────────────

    public List<VetVisitItem> listVetVisits(Long catId, Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        catOwnershipService.assertOwner(catId, userId);

        return jdbcTemplate.query(
                "SELECT vet_visit_id, visit_date, hospital_name, diagnosis, prescription, notes " +
                        "FROM vet_visit WHERE cat_id = ? ORDER BY visit_date DESC",
                new Object[]{catId},
                (rs, i) -> new VetVisitItem(
                        rs.getLong("vet_visit_id"),
                        rs.getDate("visit_date") != null ? rs.getDate("visit_date").toString() : null,
                        rs.getString("hospital_name"),
                        rs.getString("diagnosis"),
                        rs.getString("prescription"),
                        rs.getString("notes")
                )
        );
    }

    public void addVetVisit(Long catId, VetVisitCreateRequest req, Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        catOwnershipService.assertOwner(catId, userId);

        LocalDate date = req.getDate() != null && !req.getDate().isBlank()
                ? LocalDate.parse(req.getDate()) : LocalDate.now();

        jdbcTemplate.update(
                "INSERT INTO vet_visit (cat_id, user_id, visit_date, hospital_name, diagnosis, prescription, notes, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())",
                catId, userId,
                Date.valueOf(date),
                req.getHospitalName(),
                req.getDiagnosis(),
                req.getPrescription(),
                req.getNotes()
        );

        // 캘린더는 CalendarService UNION 쿼리로 자동 표시
    }

    public void updateVetVisit(Long catId, Long visitId, VetVisitCreateRequest req, Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        catOwnershipService.assertOwner(catId, userId);

        LocalDate date = req.getDate() != null && !req.getDate().isBlank()
                ? LocalDate.parse(req.getDate()) : LocalDate.now();

        jdbcTemplate.update(
                "UPDATE vet_visit SET visit_date=?, hospital_name=?, diagnosis=?, prescription=?, notes=? " +
                        "WHERE vet_visit_id=? AND cat_id=?",
                Date.valueOf(date),
                req.getHospitalName(),
                req.getDiagnosis(),
                req.getPrescription(),
                req.getNotes(),
                visitId, catId
        );
    }

    public void deleteVetVisit(Long catId, Long visitId, Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        catOwnershipService.assertOwner(catId, userId);
        jdbcTemplate.update(
                "DELETE FROM vet_visit WHERE vet_visit_id = ? AND cat_id = ?",
                visitId, catId);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String freqLabel(String freq) {
        if ("TWICE_DAILY".equals(freq)) return "하루 2회";
        if ("AS_NEEDED".equals(freq)) return "필요시";
        return "하루 1회";
    }

    private String colorLabel(String color) {
        if ("YELLOW".equals(color)) return "노란색";
        if ("RED".equals(color)) return "붉은색";
        if ("OTHER".equals(color)) return "기타색";
        return "정상색";
    }

    private String shapeLabel(String shape) {
        if ("SOFT".equals(shape)) return "무른 변";
        if ("LIQUID".equals(shape)) return "액체 변";
        if ("NONE".equals(shape)) return "변 없음";
        return "정상 변";
    }
}
