package com.example.howscat.dto;

import lombok.Getter;

@Getter
public class VetVisitCreateRequest {
    private String date;
    private String hospitalName;
    private String diagnosis;
    private String prescription;
    private String notes;
}
