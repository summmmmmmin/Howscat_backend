package com.example.howscat.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Railway DB에 누락된 핵심 테이블들을 자동 생성합니다.
 * CareTableInitializer(medication/litter_box_record/vet_visit)보다 먼저 실행되어야 합니다.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class SchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        addWeightGoalColumnToCat();
        createHealthTypeTable();
        createHealthScheduleTable();
        createWeightRecordTable();
        createCalendarMemoTable();
        createVomitStatusTable();
        createVomitRecordTable();
        createObesityCheckRecordTable();
        createHospitalTable();
        createFavoriteHospitalTable();
        createNotificationTable();
        createAiSummaryCacheTable();

        seedHealthType();
        seedVomitStatus();
    }

    private void addWeightGoalColumnToCat() {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cat' AND COLUMN_NAME = 'weight_goal'",
                    Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE cat ADD COLUMN weight_goal FLOAT DEFAULT NULL");
                log.info("cat.weight_goal 컬럼 추가 완료");
            }
        } catch (Exception e) {
            log.warn("cat.weight_goal 컬럼 추가 실패: {}", e.getMessage());
        }
    }

    private void createHealthTypeTable() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS health_type (" +
                            "  health_type_id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                            "  name VARCHAR(100) NOT NULL," +
                            "  default_cycle_month INT DEFAULT 12," +
                            "  INDEX idx_health_type_name (name)" +
                            ")"
            );
        } catch (Exception e) {
            log.warn("health_type 테이블 초기화 실패: {}", e.getMessage());
        }
    }

    private void createHealthScheduleTable() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS health_schedule (" +
                            "  health_schedule_id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                            "  cat_id BIGINT NOT NULL," +
                            "  health_type_id BIGINT NOT NULL," +
                            "  next_date DATE," +
                            "  last_date DATE," +
                            "  alarm_enabled TINYINT(1) DEFAULT 0," +
                            "  custom_cycle_month INT DEFAULT NULL," +
                            "  INDEX idx_health_schedule_cat (cat_id)," +
                            "  INDEX idx_health_schedule_date (next_date)" +
                            ")"
            );
        } catch (Exception e) {
            log.warn("health_schedule 테이블 초기화 실패: {}", e.getMessage());
        }

        // 기존 테이블에 누락된 컬럼 추가 (이미 배포된 DB에 컬럼이 없을 경우 대비)
        for (String[] col : new String[][]{
                {"alarm_enabled",     "ALTER TABLE health_schedule ADD COLUMN alarm_enabled TINYINT(1) DEFAULT 0"},
                {"custom_cycle_month","ALTER TABLE health_schedule ADD COLUMN custom_cycle_month INT DEFAULT NULL"},
                {"last_date",         "ALTER TABLE health_schedule ADD COLUMN last_date DATE"}
        }) {
            try {
                Integer cnt = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'health_schedule' AND COLUMN_NAME = ?",
                        Integer.class, col[0]);
                if (cnt == null || cnt == 0) {
                    jdbcTemplate.execute(col[1]);
                    log.info("health_schedule.{} 컬럼 추가 완료", col[0]);
                }
            } catch (Exception e) {
                log.warn("health_schedule.{} 컬럼 추가 실패: {}", col[0], e.getMessage());
            }
        }
    }

    private void createWeightRecordTable() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS weight_record (" +
                            "  weight_record_id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                            "  cat_id BIGINT NOT NULL," +
                            "  weight DOUBLE NOT NULL," +
                            "  recorded_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "  INDEX idx_weight_cat (cat_id)," +
                            "  INDEX idx_weight_date (recorded_at)" +
                            ")"
            );
        } catch (Exception e) {
            log.warn("weight_record 테이블 초기화 실패: {}", e.getMessage());
        }

        // 기존 테이블에 누락된 컬럼 추가
        for (String[] col : new String[][]{
                {"recommended_water_ml", "ALTER TABLE weight_record ADD COLUMN recommended_water_ml DOUBLE DEFAULT NULL"},
                {"recommended_food_g",   "ALTER TABLE weight_record ADD COLUMN recommended_food_g DOUBLE DEFAULT NULL"}
        }) {
            try {
                Integer cnt = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'weight_record' AND COLUMN_NAME = ?",
                        Integer.class, col[0]);
                if (cnt == null || cnt == 0) {
                    jdbcTemplate.execute(col[1]);
                    log.info("weight_record.{} 컬럼 추가 완료", col[0]);
                }
            } catch (Exception e) {
                log.warn("weight_record.{} 컬럼 추가 실패: {}", col[0], e.getMessage());
            }
        }
    }

    private void createCalendarMemoTable() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS calendar_memo (" +
                            "  calendar_memo_id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                            "  content TEXT NOT NULL," +
                            "  memo_date DATE NOT NULL," +
                            "  cat_id BIGINT NOT NULL," +
                            "  health_type_id BIGINT DEFAULT NULL," +
                            "  user_id INT NOT NULL," +
                            "  INDEX idx_calendar_memo_cat (cat_id)," +
                            "  INDEX idx_calendar_memo_date (memo_date)" +
                            ")"
            );
        } catch (Exception e) {
            log.warn("calendar_memo 테이블 초기화 실패: {}", e.getMessage());
        }
    }

    private void createVomitStatusTable() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS vomit_status (" +
                            "  vomit_status_id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                            "  color VARCHAR(30) NOT NULL," +
                            "  shape VARCHAR(50) NOT NULL," +
                            "  severity_level VARCHAR(20) NOT NULL," +
                            "  guide_text TEXT," +
                            "  INDEX idx_vomit_status_color_shape (color, shape)" +
                            ")"
            );
        } catch (Exception e) {
            log.warn("vomit_status 테이블 초기화 실패: {}", e.getMessage());
        }
    }

    private void createVomitRecordTable() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS vomit_record (" +
                            "  vomit_record_id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                            "  cat_id BIGINT NOT NULL," +
                            "  vomit_status_id BIGINT NOT NULL," +
                            "  memo TEXT," +
                            "  ai_result VARCHAR(500)," +
                            "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "  has_hair TINYINT(1) DEFAULT 0," +
                            "  image_path VARCHAR(500)," +
                            "  INDEX idx_vomit_record_cat (cat_id)," +
                            "  INDEX idx_vomit_record_date (created_at)" +
                            ")"
            );
        } catch (Exception e) {
            log.warn("vomit_record 테이블 초기화 실패: {}", e.getMessage());
        }

        // 기존 테이블에 누락된 컬럼 추가
        for (String[] col : new String[][]{
                {"ai_result",  "ALTER TABLE vomit_record ADD COLUMN ai_result VARCHAR(500)"},
                {"memo",       "ALTER TABLE vomit_record ADD COLUMN memo TEXT"},
                {"image_path", "ALTER TABLE vomit_record ADD COLUMN image_path VARCHAR(500)"},
                {"has_hair",   "ALTER TABLE vomit_record ADD COLUMN has_hair TINYINT(1) DEFAULT 0"}
        }) {
            try {
                Integer cnt = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vomit_record' AND COLUMN_NAME = ?",
                        Integer.class, col[0]);
                if (cnt == null || cnt == 0) {
                    jdbcTemplate.execute(col[1]);
                    log.info("vomit_record.{} 컬럼 추가 완료", col[0]);
                }
            } catch (Exception e) {
                log.warn("vomit_record.{} 컬럼 추가 실패: {}", col[0], e.getMessage());
            }
        }
    }

    private void createObesityCheckRecordTable() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS obesity_check_record (" +
                            "  obesity_check_record_id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                            "  cat_id BIGINT NOT NULL," +
                            "  weight_record_id BIGINT," +
                            "  obesity_level VARCHAR(30)," +
                            "  body_fat_percent DOUBLE," +
                            "  recommended_target_weight DOUBLE," +
                            "  recommended_water DOUBLE," +
                            "  recommended_food DOUBLE," +
                            "  checked_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "  INDEX idx_obesity_cat (cat_id)," +
                            "  INDEX idx_obesity_date (checked_at)" +
                            ")"
            );
        } catch (Exception e) {
            log.warn("obesity_check_record 테이블 초기화 실패: {}", e.getMessage());
        }
    }

    private void createHospitalTable() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS hospital (" +
                            "  hospital_id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                            "  name VARCHAR(200) NOT NULL," +
                            "  address VARCHAR(500)," +
                            "  latitude DOUBLE," +
                            "  longitude DOUBLE," +
                            "  phone VARCHAR(30)," +
                            "  is_24h TINYINT(1) DEFAULT 0," +
                            "  is_operating TINYINT(1) DEFAULT 1," +
                            "  rating DOUBLE DEFAULT 0.0," +
                            "  INDEX idx_hospital_name (name)" +
                            ")"
            );
        } catch (Exception e) {
            log.warn("hospital 테이블 초기화 실패: {}", e.getMessage());
        }
    }

    private void createFavoriteHospitalTable() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS favorite_hospital (" +
                            "  user_id INT NOT NULL," +
                            "  hospital_id BIGINT NOT NULL," +
                            "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                            "  PRIMARY KEY (user_id, hospital_id)" +
                            ")"
            );
        } catch (Exception e) {
            log.warn("favorite_hospital 테이블 초기화 실패: {}", e.getMessage());
        }
    }

    private void createNotificationTable() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS notification (" +
                            "  notification_id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                            "  user_id INT NOT NULL," +
                            "  cat_id BIGINT," +
                            "  related_type VARCHAR(30)," +
                            "  related_id BIGINT," +
                            "  title VARCHAR(200)," +
                            "  content TEXT," +
                            "  scheduled_at DATETIME," +
                            "  is_sent TINYINT(1) DEFAULT 0," +
                            "  sent_at DATETIME DEFAULT NULL," +
                            "  INDEX idx_notification_user (user_id)," +
                            "  INDEX idx_notification_cat (cat_id)," +
                            "  INDEX idx_notification_scheduled (scheduled_at)" +
                            ")"
            );
        } catch (Exception e) {
            log.warn("notification 테이블 초기화 실패: {}", e.getMessage());
        }
    }

    private void createAiSummaryCacheTable() {
        try {
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS ai_health_summary_cache (" +
                "  cat_id BIGINT PRIMARY KEY," +
                "  summary TEXT NOT NULL," +
                "  generated_at DATETIME NOT NULL" +
                ")");
            log.info("ai_health_summary_cache 테이블 준비 완료");
        } catch (Exception e) {
            log.warn("ai_health_summary_cache 테이블 생성 실패: {}", e.getMessage());
        }
    }

    // ─── Seed Data ────────────────────────────────────────────────────────────

    private void seedHealthType() {
        try {
            jdbcTemplate.execute(
                    "INSERT IGNORE INTO health_type (health_type_id, name, default_cycle_month) VALUES " +
                            "(1, '건강검진', 12)," +
                            "(2, '예방접종', 12)," +
                            "(3, '구충제', 3)," +
                            "(4, '치과검진', 12)," +
                            "(5, '비만도검사', 6)," +
                            "(6, '토분석', 0)"
            );
        } catch (Exception e) {
            log.warn("health_type 시드 데이터 삽입 실패: {}", e.getMessage());
        }
    }

    private void seedVomitStatus() {
        try {
            // color × shape 조합 핵심 케이스 삽입 (INSERT IGNORE로 중복 방지)
            String[][] rows = {
                    // WHITE
                    {"WHITE", "NORMAL",        "LOW",    "흰색 거품이 없는 구토는 대개 공복 구토입니다. 소량이면 걱정하지 마세요."},
                    {"WHITE", "FOAM",          "LOW",    "흰 거품 구토는 공복이 원인일 수 있습니다. 식사 간격을 줄여 보세요."},
                    {"WHITE", "FOOD",          "LOW",    "미소화 사료가 섞인 구토입니다. 급하게 먹지 않도록 도와주세요."},
                    {"WHITE", "FOAM_FOOD",     "MEDIUM", "거품+사료 혼합 구토입니다. 며칠 지속되면 수의사에게 보여주세요."},
                    {"WHITE", "FOREIGN",       "HIGH",   "이물질이 포함된 구토입니다. 즉시 수의사 진료를 받으세요."},
                    {"WHITE", "FOAM_FOREIGN",  "HIGH",   "이물질+거품 구토입니다. 즉시 수의사 진료가 필요합니다."},
                    {"WHITE", "FOOD_FOREIGN",  "HIGH",   "사료+이물질 구토입니다. 즉시 수의사 진료를 받으세요."},
                    {"WHITE", "FOAM_FOOD_FOREIGN", "HIGH", "복합 이물질 구토입니다. 즉시 응급 진료가 필요합니다."},
                    // YELLOW
                    {"YELLOW", "NORMAL",       "MEDIUM", "노란 담즙 구토는 공복 상태일 때 자주 발생합니다. 자주 반복되면 진료를 권장합니다."},
                    {"YELLOW", "FOAM",         "MEDIUM", "노란 거품 구토는 위장 자극 신호일 수 있습니다. 하루 이상 지속되면 진료 받으세요."},
                    {"YELLOW", "FOOD",         "MEDIUM", "노란 담즙+사료 구토입니다. 식이 관리가 필요합니다."},
                    {"YELLOW", "FOAM_FOOD",    "MEDIUM", "노란 거품+사료 혼합 구토입니다. 반복되면 진료를 권장합니다."},
                    {"YELLOW", "FOREIGN",      "HIGH",   "노란 구토에 이물질이 포함되어 있습니다. 즉시 진료 받으세요."},
                    {"YELLOW", "FOAM_FOREIGN", "HIGH",   "노란 거품+이물질 구토입니다. 즉시 수의사 진료가 필요합니다."},
                    {"YELLOW", "FOOD_FOREIGN", "HIGH",   "노란 구토에 이물질+사료가 포함되어 있습니다. 즉시 진료 받으세요."},
                    {"YELLOW", "FOAM_FOOD_FOREIGN", "HIGH", "복합 노란 구토입니다. 즉시 응급 진료가 필요합니다."},
                    // GREEN
                    {"GREEN", "NORMAL",        "MEDIUM", "녹색 구토는 담즙 과다 분비 신호일 수 있습니다. 진료를 권장합니다."},
                    {"GREEN", "FOAM",          "HIGH",   "녹색 거품 구토는 장 역류 신호일 수 있습니다. 빠른 진료가 필요합니다."},
                    {"GREEN", "FOOD",          "MEDIUM", "녹색 담즙+사료 구토입니다. 진료를 받아보세요."},
                    {"GREEN", "FOREIGN",       "HIGH",   "녹색 구토에 이물질이 포함되어 있습니다. 즉시 진료 받으세요."},
                    {"GREEN", "FOAM_FOOD",     "HIGH",   "녹색 거품+사료 구토입니다. 빠른 진료를 권장합니다."},
                    {"GREEN", "FOAM_FOREIGN",  "HIGH",   "녹색 거품+이물질 구토입니다. 즉시 응급 진료가 필요합니다."},
                    {"GREEN", "FOOD_FOREIGN",  "HIGH",   "녹색 구토+이물질입니다. 즉시 응급 진료가 필요합니다."},
                    {"GREEN", "FOAM_FOOD_FOREIGN", "HIGH", "복합 녹색 구토입니다. 즉시 응급 진료가 필요합니다."},
                    // RED
                    {"RED", "NORMAL",          "HIGH",   "혈액이 포함된 구토입니다. 즉시 수의사 응급 진료를 받으세요."},
                    {"RED", "FOAM",            "HIGH",   "혈액+거품 구토입니다. 즉시 응급 진료가 필요합니다."},
                    {"RED", "FOOD",            "HIGH",   "혈액+사료 구토입니다. 즉시 응급 진료가 필요합니다."},
                    {"RED", "FOREIGN",         "HIGH",   "혈액+이물질 구토입니다. 즉시 응급 진료가 필요합니다."},
                    {"RED", "FOAM_FOOD",       "HIGH",   "혈액+거품+사료 구토입니다. 즉시 응급 진료가 필요합니다."},
                    {"RED", "FOAM_FOREIGN",    "HIGH",   "혈액+이물질 복합 구토입니다. 즉시 응급 진료가 필요합니다."},
                    {"RED", "FOOD_FOREIGN",    "HIGH",   "혈액+사료+이물질 구토입니다. 즉시 응급 진료가 필요합니다."},
                    {"RED", "FOAM_FOOD_FOREIGN", "HIGH", "혈액 복합 구토입니다. 즉시 응급 진료가 필요합니다."},
                    // BROWN
                    {"BROWN", "NORMAL",        "MEDIUM", "갈색 구토는 오래된 사료나 혈액이 원인일 수 있습니다. 진료를 권장합니다."},
                    {"BROWN", "FOAM",          "HIGH",   "갈색 거품 구토입니다. 빠른 진료를 권장합니다."},
                    {"BROWN", "FOOD",          "MEDIUM", "갈색 사료 구토입니다. 반복되면 진료 받으세요."},
                    {"BROWN", "FOREIGN",       "HIGH",   "갈색 구토에 이물질이 포함되어 있습니다. 즉시 진료 받으세요."},
                    {"BROWN", "FOAM_FOOD",     "HIGH",   "갈색 거품+사료 구토입니다. 빠른 진료를 권장합니다."},
                    {"BROWN", "FOAM_FOREIGN",  "HIGH",   "갈색 거품+이물질 구토입니다. 즉시 응급 진료가 필요합니다."},
                    {"BROWN", "FOOD_FOREIGN",  "HIGH",   "갈색 사료+이물질 구토입니다. 즉시 응급 진료가 필요합니다."},
                    {"BROWN", "FOAM_FOOD_FOREIGN", "HIGH", "갈색 복합 구토입니다. 즉시 응급 진료가 필요합니다."},
                    // BLACK
                    {"BLACK", "NORMAL",        "HIGH",   "검은색 구토는 내출혈 신호일 수 있습니다. 즉시 응급 진료가 필요합니다."},
                    {"BLACK", "FOAM",          "HIGH",   "검은 거품 구토입니다. 즉시 응급 진료가 필요합니다."},
                    {"BLACK", "FOOD",          "HIGH",   "검은색 구토+사료입니다. 즉시 응급 진료가 필요합니다."},
                    {"BLACK", "FOREIGN",       "HIGH",   "검은색 구토+이물질입니다. 즉시 응급 진료가 필요합니다."},
                    {"BLACK", "FOAM_FOOD",     "HIGH",   "검은 거품+사료 구토입니다. 즉시 응급 진료가 필요합니다."},
                    {"BLACK", "FOAM_FOREIGN",  "HIGH",   "검은 거품+이물질 구토입니다. 즉시 응급 진료가 필요합니다."},
                    {"BLACK", "FOOD_FOREIGN",  "HIGH",   "검은색 복합 구토입니다. 즉시 응급 진료가 필요합니다."},
                    {"BLACK", "FOAM_FOOD_FOREIGN", "HIGH", "검은색 복합 구토입니다. 즉시 응급 진료가 필요합니다."},
                    // UNKNOWN
                    {"UNKNOWN", "NORMAL",      "LOW",    "사진 분석이 어렵습니다. 이상이 지속되면 수의사 상담을 받아보세요."},
                    {"UNKNOWN", "FOAM",        "MEDIUM", "분석 불명 거품 구토입니다. 반복되면 수의사 진료를 권장합니다."},
                    {"UNKNOWN", "FOOD",        "LOW",    "사진 분석이 어렵습니다. 소량이면 크게 걱정하지 않아도 됩니다."},
                    {"UNKNOWN", "FOREIGN",     "HIGH",   "이물질이 포함된 구토입니다. 색상과 무관하게 즉시 진료 받으세요."},
                    {"UNKNOWN", "FOAM_FOOD",   "MEDIUM", "거품+사료 구토입니다. 반복되면 수의사 진료를 권장합니다."},
                    {"UNKNOWN", "FOAM_FOREIGN","HIGH",   "거품+이물질 구토입니다. 즉시 응급 진료가 필요합니다."},
                    {"UNKNOWN", "FOOD_FOREIGN","HIGH",   "사료+이물질 구토입니다. 즉시 응급 진료가 필요합니다."},
                    {"UNKNOWN", "FOAM_FOOD_FOREIGN", "HIGH", "복합 구토입니다. 즉시 응급 진료가 필요합니다."},
            };

            for (String[] row : rows) {
                try {
                    jdbcTemplate.update(
                            "INSERT IGNORE INTO vomit_status (color, shape, severity_level, guide_text) " +
                                    "SELECT ?, ?, ?, ? FROM DUAL " +
                                    "WHERE NOT EXISTS (SELECT 1 FROM vomit_status WHERE color = ? AND shape = ?)",
                            row[0], row[1], row[2], row[3], row[0], row[1]
                    );
                } catch (Exception e) {
                    log.warn("vomit_status 시드 삽입 실패 [{}][{}]: {}", row[0], row[1], e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("vomit_status 시드 데이터 전체 실패: {}", e.getMessage());
        }
    }
}
