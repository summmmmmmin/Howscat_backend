<div align="center">

# Howscat — Backend

**고양이 건강 관리 앱 Howscat의 Spring Boot 백엔드**

[![Spring Boot](https://img.shields.io/badge/Spring_Boot_4.0.2-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io)
[![Railway](https://img.shields.io/badge/Live_Deploy-Railway-0B0D0E?style=flat-square&logo=railway&logoColor=white)](https://railway.app)
[![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=flat-square&logo=mysql&logoColor=white)](https://www.mysql.com)
[![Gemini](https://img.shields.io/badge/Gemini_2.5_Flash-4285F4?style=flat-square&logo=google&logoColor=white)](https://deepmind.google/technologies/gemini/)
[![JWT](https://img.shields.io/badge/JWT-Auth-000000?style=flat-square&logo=jsonwebtokens&logoColor=white)](#)

<br>

> Railway에 실배포 중인 REST API 서버
> Android 클라이언트 → [Howscat App](../README.md)

</div>

---

## 개요

Howscat Android 앱의 전용 백엔드. JWT 인증, Gemini AI 연동, 고양이별 건강 기록 관리, 주변 병원 검색(Kakao Local 프록시)을 담당한다.

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| 프레임워크 | Spring Boot 4.0.2 |
| 언어 | Java 17 |
| DB 접근 | Spring Data JPA + JdbcTemplate (단순 CRUD는 JPA, 복잡 집계·UNION 쿼리는 JdbcTemplate) |
| 인증 | JWT — Access Token + Refresh Token |
| Cache / 인증 저장 | Redis — Refresh Token 저장 / Blacklist / AI 요약 캐시 |
| AI | Google Gemini 2.5 Flash (Vision · Text) |
| 외부 API | Kakao Local (병원 검색 프록시) |
| 배포 | Railway (MySQL + Redis + Spring Boot 컨테이너) |
| 스키마 | ApplicationRunner — 배포 시 DDL 자동 실행 |

---

## 주요 설계 결정

### JPA + JdbcTemplate 하이브리드
User·Cat 등 단순 CRUD는 `Spring Data JPA`로 처리하고, 8개 데이터 소스를 통합하는 캘린더 UNION ALL 쿼리나 `INFORMATION_SCHEMA` 조회처럼 ORM으로 표현하기 어려운 복잡한 쿼리는 `JdbcTemplate`으로 직접 작성한다. 기능 복잡도에 따라 두 방식을 선택적으로 혼용한다.

### Railway 자동 스키마
Railway는 빈 DB만 제공한다. `SchemaInitializer`(`@Order(1)`)와 `CareTableInitializer`(`@Order(2)`)가 앱 시작 시 16개 테이블을 `CREATE TABLE IF NOT EXISTS`로 생성하고, 기존 테이블에 컬럼이 누락된 경우 `INFORMATION_SCHEMA`를 조회해 `ALTER TABLE`을 실행한다.
재배포 시 수동 마이그레이션 **0건**.

### Gemini 이중 활용
- **Vision** — 토사물 이미지(Base64) → 색상·형태·위험도 JSON 구조화 분석
- **Text** — 최근 7일 케어 데이터 집계 → 한 줄 건강 조언 생성 (6시간 캐시)

### catId 소유권 검증
모든 `/cats/{catId}/...` 엔드포인트에서 JWT Principal(userId)과 catId의 소유권을 DB 조회로 검증한다. 다른 사용자의 데이터에 접근할 수 없다.

---

## API 목록

### 인증
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/users/login` | 로그인 → JWT 발급 |
| POST | `/api/users/signup` | 회원가입 |
| POST | `/api/users/logout` | 로그아웃 |
| POST | `/api/users/refresh` | Access Token 갱신 |

### 고양이
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/cats` | 고양이 등록 |
| GET | `/cats/{id}` | 정보 조회 |
| GET | `/cats/user` | 내 고양이 목록 |
| POST | `/cats/select/{catId}` | 활성 고양이 전환 |

### AI 분석
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/cats/{id}/vomit` | 토사물 Gemini Vision 분석 + 저장 |
| DELETE | `/cats/{id}/vomit/{vomitId}` | 구토 기록 삭제 |
| GET | `/cats/{id}/ai-summary` | Gemini Text 건강 요약 (6h 캐시) |

### 케어 & 기록
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/cats/{id}/care-weight` | 체중·사료량 계산 및 저장 |
| POST | `/cats/{id}/obesity-check` | 비만도 검사 |
| GET | `/cats/{id}/weight-history` | 체중 이력 (추천 물·사료량 포함) |
| GET | `/cats/{id}/obesity-history` | 비만도 이력 |
| GET/POST | `/cats/{id}/medications` | 투약 목록·등록 |
| PUT/DELETE | `/cats/{id}/medications/{id}` | 수정·삭제 |
| GET/POST | `/cats/{id}/litter-records` | 화장실 기록 |
| PUT/DELETE | `/cats/{id}/litter-records/{id}` | 수정·삭제 |
| GET/POST | `/cats/{id}/vet-visits` | 진료 기록 |
| PUT/DELETE | `/cats/{id}/vet-visits/{id}` | 수정·삭제 |

### 건강 일정
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/cats/{id}/health-schedules` | 건강검진·예방접종 일정 목록 |
| POST | `/cats/{id}/health-schedules` | 일정 등록 |
| PUT | `/cats/{id}/health-schedules/{scheduleId}` | 일정 수정 |
| DELETE | `/cats/{id}/health-schedules/{scheduleId}` | 일정 삭제 |

### 캘린더
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/cats/{id}/calendar?from=&to=` | 기간별 통합 이벤트 (UNION ALL) |
| POST | `/cats/{id}/calendar-memos` | 메모 작성 |
| PUT | `/cats/{id}/calendar-memos/{memoId}` | 메모 수정 |
| DELETE | `/cats/{id}/calendar-memos/{memoId}` | 메모 삭제 |

### 병원
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/hospitals/nearby?lat=&lng=&radius=` | Kakao Local 병원 검색 |
| GET | `/hospitals/favorites` | 즐겨찾기 목록 |
| POST | `/hospitals/{id}/favorite` | 즐겨찾기 등록 |
| DELETE | `/hospitals/{id}/favorite` | 즐겨찾기 해제 |

---

## 캘린더 이벤트 타입

캘린더 API는 여러 테이블을 단일 응답으로 통합 반환한다.

| eventType | 출처 테이블 | 설명 |
|-----------|------------|------|
| `MEMO` | calendar_memo | 일반 메모 |
| `HEALTH_CHECKUP` | health_schedule | 건강검진 (완료·예정) |
| `HEALTH_VACCINE` | health_schedule | 예방접종 (완료·예정) |
| `WEIGHT` | weight_record | 체중 기록 |
| `VOMIT` | vomit_record | 구토 분석 기록 |
| `MEDICATION` | medication | 투약 기록 |
| `LITTER_BOX` | litter_box_record | 화장실 기록 |
| `VET_VISIT` | vet_visit | 진료 기록 |

---

## DB 스키마

| 테이블 | 설명 |
|--------|------|
| `users` | 사용자 계정 |
| `cat` | 고양이 정보 |
| `health_type` | 건강 검진 유형 시드 |
| `health_schedule` | 건강검진·예방접종 일정 |
| `weight_record` | 체중 + 추천 물·사료량 |
| `obesity_check_record` | 비만도 검사 결과 |
| `vomit_status` | 구토 상태 기준 (7색×8형태 = 56행 시드) |
| `vomit_record` | 구토 기록 + AI 분석 결과 |
| `calendar_memo` | 캘린더 메모 |
| `medication` | 투약 기록 |
| `litter_box_record` | 화장실 기록 |
| `vet_visit` | 진료 기록 |
| `hospital` | 병원 정보 캐시 |
| `favorite_hospital` | 즐겨찾기 병원 |
| `notification` | 알림 기록 |
| `ai_health_summary_cache` | AI 건강 요약 캐시 (6h TTL) |

---

## 로컬 실행

### 요구사항
- JDK 17
- MySQL 8.x (로컬) 또는 Railway 연결

### 환경 변수 설정

| 변수 | 설명 |
|------|------|
| `DB_URL` | JDBC URL (`jdbc:mysql://...`) |
| `DB_USERNAME` | DB 사용자명 |
| `DB_PASSWORD` | DB 비밀번호 |
| `REDIS_HOST` | Redis 호스트 (기본값: localhost) |
| `REDIS_PORT` | Redis 포트 (기본값: 6379) |
| `REDIS_PASSWORD` | Redis 비밀번호 |
| `REDIS_USERNAME` | Redis 사용자명 (기본값: default) |
| `JWT_SECRET` | JWT 서명 키 (32바이트 이상) |
| `KAKAO_REST_API_KEY` | Kakao Local REST API 키 |

### 실행

```bash
./gradlew bootRun
```

테이블은 시작 시 자동 생성되므로 별도 DDL 실행 불필요.

---

## 프로젝트 구조

```
src/main/java/com/example/howscat/
├── config/
│   ├── SchemaInitializer.java   # 앱 시작 시 DDL 자동 실행 (@Order 1)
│   ├── CareTableInitializer.java # 케어 테이블 DDL (@Order 2)
│   ├── RedisConfig.java          # Redis 연결 설정
│   ├── SwaggerConfig.java        # OpenAPI 문서 설정
│   └── SecurityConfig.java      # JWT 필터 체인
├── controller/                  # REST 엔드포인트
├── service/
│   ├── AiHealthSummaryService   # Gemini Text 건강 요약
│   ├── VomitAnalysisService     # Gemini Vision 구토 분석
│   ├── CalendarService          # UNION ALL 통합 쿼리
│   ├── ObesityCheckService      # 비만도·체중 계산
│   └── HospitalService          # Kakao Local 프록시
└── dto/                         # 요청/응답 모델
```

---

<div align="center">

**Spring Boot 4.0.2 · Railway · Gemini 2.5 Flash · JWT · Spring Data JPA + JdbcTemplate**

</div>
