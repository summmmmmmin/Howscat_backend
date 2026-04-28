package com.example.howscat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HealthScheduleItem {

    private Long healthScheduleId;
    private Long healthTypeId;

    private String healthTypeName;
    private String catName;

    // yyyy-MM-dd
    private String nextDate;

    // effective cycle month (custom > default rule)
    private Integer effectiveCycleMonth;

    private Boolean alarmEnabled;
    private Integer customCycleMonth;
}

