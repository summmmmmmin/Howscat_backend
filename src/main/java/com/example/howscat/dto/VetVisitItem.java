package com.example.howscat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VetVisitItem {
    private Long visitId;
    private String date;
    private String hospitalName;
    private String diagnosis;
    private String prescription;
    private String notes;
}
