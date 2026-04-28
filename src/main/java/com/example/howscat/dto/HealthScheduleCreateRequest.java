package com.example.howscat.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 건강검진/예방접종 일정 신규 등록 요청 DTO
 * - healthTypeId: 1=건강검진, 2=예방접종 (health_type 테이블 PK)
 * - lastDate: 이 검진/접종을 받은 날짜 (yyyy-MM-dd). 다음 일정 계산 기준점.
 * - alarmEnabled: 다음 일정 알림 사용 여부
 * - customCycleMonth: 커스텀 주기(개월). null이면 나이·종류 기반 자동 계산.
 */
@Getter
@Setter
@NoArgsConstructor
public class HealthScheduleCreateRequest {

    private Long healthTypeId;
    private String lastDate;       // yyyy-MM-dd, 검진/접종 받은 날
    private Boolean alarmEnabled;
    private Integer customCycleMonth;
}
