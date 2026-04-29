package com.example.howscat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MedicationItem {
    private Long medicationId;
    private String name;
    private String dosage;
    private String frequency;
    private String startDate;
    private String endDate;
    private Boolean alarmEnabled;
    private Integer alarmHour;
    private Integer alarmMinute;
    private Integer alarmHour2;
    private Integer alarmMinute2;
    private String notes;
}
