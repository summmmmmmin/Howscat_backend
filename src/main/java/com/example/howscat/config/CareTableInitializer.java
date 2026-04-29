package com.example.howscat.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * medication / litter_box_record / vet_visit 테이블이 없을 경우 자동 생성
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class CareTableInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        createMedicationTable();
        createLitterBoxRecordTable();
        createVetVisitTable();
    }

    private void createMedicationTable() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS medication (" +
                            "  medication_id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                            "  cat_id BIGINT NOT NULL," +
                            "  user_id INT NOT NULL," +
                            "  name VARCHAR(100) NOT NULL," +
                            "  dosage VARCHAR(100)," +
                            "  frequency VARCHAR(20) DEFAULT 'DAILY'," +
                            "  start_date DATE NOT NULL," +
                            "  end_date DATE," +
                            "  alarm_enabled TINYINT(1) DEFAULT 0," +
                            "  alarm_hour INT DEFAULT 9," +
                            "  alarm_minute INT DEFAULT 0," +
                            "  alarm_hour2 INT DEFAULT NULL," +
                            "  alarm_minute2 INT DEFAULT NULL," +
                            "  notes TEXT," +
                            "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "  INDEX idx_medication_cat (cat_id)," +
                            "  INDEX idx_medication_date (start_date)" +
                            ")"
            );
        } catch (Exception e) {
            log.warn("medication 테이블 초기화 실패 (이미 존재할 수 있음): {}", e.getMessage());
        }

        // 기존 테이블에 alarm_hour2 / alarm_minute2 컬럼이 없으면 추가
        for (String[] col : new String[][]{
                {"alarm_hour2",   "ALTER TABLE medication ADD COLUMN alarm_hour2 INT DEFAULT NULL"},
                {"alarm_minute2", "ALTER TABLE medication ADD COLUMN alarm_minute2 INT DEFAULT NULL"}
        }) {
            try {
                Integer cnt = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'medication' AND COLUMN_NAME = ?",
                        Integer.class, col[0]);
                if (cnt == null || cnt == 0) {
                    jdbcTemplate.execute(col[1]);
                    log.info("medication.{} 컬럼 추가 완료", col[0]);
                }
            } catch (Exception e) {
                log.warn("medication.{} 컬럼 추가 실패: {}", col[0], e.getMessage());
            }
        }
    }

    private void createLitterBoxRecordTable() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS litter_box_record (" +
                            "  litter_box_record_id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                            "  cat_id BIGINT NOT NULL," +
                            "  user_id INT NOT NULL," +
                            "  record_date DATE NOT NULL," +
                            "  count INT DEFAULT 1," +
                            "  color VARCHAR(20) DEFAULT 'NORMAL'," +
                            "  shape VARCHAR(20) DEFAULT 'NORMAL'," +
                            "  abnormal TINYINT(1) DEFAULT 0," +
                            "  notes TEXT," +
                            "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "  INDEX idx_litter_cat (cat_id)," +
                            "  INDEX idx_litter_date (record_date)" +
                            ")"
            );
        } catch (Exception e) {
            log.warn("litter_box_record 테이블 초기화 실패 (이미 존재할 수 있음): {}", e.getMessage());
        }
    }

    private void createVetVisitTable() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS vet_visit (" +
                            "  vet_visit_id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                            "  cat_id BIGINT NOT NULL," +
                            "  user_id INT NOT NULL," +
                            "  visit_date DATE NOT NULL," +
                            "  hospital_name VARCHAR(200)," +
                            "  diagnosis VARCHAR(500)," +
                            "  prescription VARCHAR(500)," +
                            "  notes TEXT," +
                            "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "  INDEX idx_vet_cat (cat_id)," +
                            "  INDEX idx_vet_date (visit_date)" +
                            ")"
            );
        } catch (Exception e) {
            log.warn("vet_visit 테이블 초기화 실패 (이미 존재할 수 있음): {}", e.getMessage());
        }
    }
}
