package com.example.howscat.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class HealthScheduleUpdateRequest {

    // yyyy-MM-dd (선택)
    private String nextDate;

    // 개월(선택) - null이면 기존값 유지
    private Integer customCycleMonth;

    private Boolean alarmEnabled;
}

